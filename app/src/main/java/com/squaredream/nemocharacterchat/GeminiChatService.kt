package com.squaredream.nemocharacterchat.data

import android.R.attr.content
import android.R.id.content
import android.util.Log
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Gemini API를 사용하여 채팅 기능을 제공하는 서비스 클래스
 */
class GeminiChatService {

    data class StreamingChatResponse(
        val isComplete: Boolean,  // 응답이 완료되었는지 여부
        val text: String?,        // 현재까지 받은 응답 텍스트
        val error: String?        // 오류 메시지 (있는 경우)
    )

    companion object {
        private const val TAG = "GeminiChatService"
        private const val MODEL_NAME = "gemini-2.5-flash-preview-04-17"

        // 세션 초기화 작업과 결과를 추적하는 맵
        // 키: "$characterId:$apiKey", 값: 진행 중인 초기화 작업
        private val sessionInitTasks = mutableMapOf<String, kotlinx.coroutines.Deferred<CharacterChatInfo?>>()
        
        // 세션 초기화 작업 동기화를 위한 뮤텍스
        private val sessionInitMutex = kotlinx.coroutines.sync.Mutex()

        // 캐릭터별 세션 캐시
        private var characterChats = mutableMapOf<String, CharacterChatInfo>()

        // 모델 캐싱 - API 키별로 GenerativeModel 인스턴스 재사용
        private var generativeModels = mutableMapOf<String, GenerativeModel>()

        // 프롬프트 캐싱
        private val cachedPrompts = mutableMapOf<String, String>()

        /**
         * 캐릭터 채팅 정보를 저장하는 데이터 클래스
         */
        private data class CharacterChatInfo(
            val chat: Chat,              // Gemini 채팅 객체
            val apiKey: String,          // 사용된 API 키
            val characterId: String,     // 캐릭터 ID
            val isInitialized: Boolean,   // 초기화 여부
            val lastActivity: Long
        )

        /**
         * API 키에 해당하는 GenerativeModel을 가져오거나 생성합니다.
         * 이미 생성된 모델이 있으면 재사용하여 리소스를 절약합니다.
         */
        private fun getOrCreateModel(apiKey: String): GenerativeModel {
            // 이미 생성된 모델이 있으면 재사용
            return generativeModels[apiKey] ?: run {
                // 없으면 새로 생성하고 캐시에 저장
                val model = GenerativeModel(
                    modelName = MODEL_NAME,
                    apiKey = apiKey
                )
                generativeModels[apiKey] = model
                model
            }
        }

        /**
         * API 키가 유효한지 테스트합니다.
         */
        suspend fun testApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
            try {
                // 캐싱된 모델 사용
                val generativeModel = getOrCreateModel(apiKey)

                // 간단한 테스트 메시지로 API 키 확인
                val response = generativeModel.generateContent("Hello")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "API key validation failed: ${e.message}", e)
                // 유효하지 않은 API 키면 캐시에서 제거
                generativeModels.remove(apiKey)
                return@withContext false
            }
        }

        /**
         * 첫 번째 채팅 교환을 수행합니다 (초기화 및 첫 응답).
         * 최적화된 버전 - 불필요한 중복 세션 생성 방지
         */
        suspend fun performInitialExchange(
            apiKey: String,
            characterId: String
        ): String = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting initial exchange for character: $characterId")

                // 세션 키 (캐릭터ID:API키)
                val sessionKey = "$characterId:$apiKey"

                // 1. 기존 세션 정리
                clearCharacterChat(characterId)

                // 2. 모델과 프롬프트 준비
                val generativeModel = getOrCreateModel(apiKey)
                val characterPrompt = cachedPrompts[characterId] ?: run {
                    // 캐릭터 저장소에서 프롬프트 가져오기
                    val prompt = CharacterRepository.getCharacterPrompt(characterId)
                    cachedPrompts[characterId] = prompt
                    prompt
                }

                // 3. 이 초기화 단계에서는 startChat()을 통해 바로 세션 시작
                //    - generateContent()와 startChat() 중복 호출 방지
                Log.d(TAG, "Creating chat session with character prompt")
                val chat = generativeModel.startChat()
                
                // 4. 페르소나 설정 및 초기 메시지 받기 (단일 요청으로 통합)
                val response = chat.sendMessage(characterPrompt)
                val initialMessage = response.text ?: "안녕하세요, 여행자."
                
                Log.d(TAG, "Initial message received: ${initialMessage.take(50)}...")

                // 5. 세션 정보 저장
                val characterChat = CharacterChatInfo(
                    chat = chat,
                    apiKey = apiKey,
                    characterId = characterId,
                    isInitialized = true,
                    lastActivity = System.currentTimeMillis()
                )

                // 세션 캐시에 저장
                characterChats[sessionKey] = characterChat
                
                Log.d(TAG, "Chat session initialized and cached")
                
                return@withContext initialMessage
            } catch (e: Exception) {
                Log.e(TAG, "Error during initial exchange: ${e.message}", e)
                return@withContext "죄송합니다, 대화를 시작하는 중 오류가 발생했습니다. 다시 시도해 주세요."
            }
        }

        /**
         * 캐릭터의 채팅 세션을 초기화합니다.
         * 이미 초기화된 세션이 있다면 재사용하고, 없으면 새로 생성합니다.
         * 동시에 여러 곳에서 같은 세션 초기화를 요청하면 하나의 작업만 실행되도록 동기화합니다.
         */
        private suspend fun initializeCharacterChat(
            apiKey: String,
            characterId: String,
            forceReinitialize: Boolean = false
        ): CharacterChatInfo = withContext(Dispatchers.IO) {
            // 세션 키 (캐릭터ID:API키)
            val chatKey = "$characterId:$apiKey"

            // 강제 재초기화인 경우 기존 세션 및 진행중인 작업 제거
            if (forceReinitialize) {
                sessionInitMutex.withLock {
                    characterChats.remove(chatKey)
                    sessionInitTasks.remove(chatKey)
                }
            }

            // 기존 세션이 있고 재초기화가 아니면 재사용
            characterChats[chatKey]?.let { existingChat ->
                if (existingChat.apiKey == apiKey && existingChat.isInitialized && !forceReinitialize) {
                    // 마지막 활동 시간 업데이트
                    characterChats[chatKey] = existingChat.copy(lastActivity = System.currentTimeMillis())
                    Log.d(TAG, "Reusing existing chat for character: $characterId")
                    return@withContext existingChat
                }
            }

            // 초기화 작업이 이미 진행 중인지 확인하고 작업을 시작하거나 기존 작업 결과를 대기
            val initTask = sessionInitMutex.withLock {
                // 이미 진행 중인 작업이 있으면 그것을 사용
                sessionInitTasks[chatKey] ?: coroutineScope {
                    // 없으면 새 작업 시작
                    val newTask = async {
                        try {
                            // 새 세션 생성
                            Log.d(TAG, "Creating new chat for character: $characterId")

                            // 캐싱된 Gemini 모델 가져오기
                            val generativeModel = getOrCreateModel(apiKey)

                            // 캐릭터 프롬프트 가져오기
                            val characterPrompt = cachedPrompts[characterId] ?: run {
                                // 없으면 CharacterRepository에서 가져와서 캐싱
                                val prompt = CharacterRepository.getCharacterPrompt(characterId)
                                cachedPrompts[characterId] = prompt
                                prompt
                            }

                            // 새 채팅 세션 시작
                            val chat = generativeModel.startChat()

                            // 항상 페르소나 설정 프롬프트 전송 (이전 초기화 상태 무시)
                            try {
                                Log.d(TAG, "Sending character persona prompt")
                                val response = chat.sendMessage(characterPrompt)
                                Log.d(TAG, "Character persona set successfully: ${response.text?.take(50)}...")
                                
                                // 새 채팅 정보 생성
                                val characterChat = CharacterChatInfo(
                                    chat = chat,
                                    apiKey = apiKey,
                                    characterId = characterId,
                                    isInitialized = true,
                                    lastActivity = System.currentTimeMillis()
                                )

                                // 세션 캐시에 저장
                                characterChats[chatKey] = characterChat
                                
                                characterChat
                            } catch (e: Exception) {
                                Log.e(TAG, "Error setting character persona: ${e.message}", e)
                                null
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error creating chat session: ${e.message}", e)
                            null
                        } finally {
                            // 작업 완료 후 맵에서 제거
                            sessionInitMutex.withLock {
                                sessionInitTasks.remove(chatKey)
                            }
                        }
                    }
                    
                    // 새 작업 등록
                    sessionInitTasks[chatKey] = newTask
                    newTask
                }
            }

            // 작업 결과 대기 및 반환
            val result = initTask.await()
            
            if (result != null) {
                return@withContext result
            } else {
                throw Exception("Failed to initialize chat session for character: $characterId")
            }
        }

        /**
         * 캐릭터 세션이 이미 존재하는지 확인합니다.
         *
         * @param apiKey API 키
         * @param characterId 캐릭터 ID
         * @return 세션이 존재하면 true, 아니면 false
         */
        suspend fun checkSessionExists(apiKey: String, characterId: String): Boolean = withContext(Dispatchers.IO) {
            val chatKey = "$characterId:$apiKey"
            val exists = characterChats.containsKey(chatKey) && characterChats[chatKey]?.isInitialized == true
            Log.d(TAG, "Session exists for $characterId: $exists")
            return@withContext exists
        }

        /**
         * 환영 메시지를 가져옵니다.
         * 기존 세션이 있는 경우에 사용하며, 세션을 재사용합니다.
         *
         * @param apiKey API 키
         * @param characterId 캐릭터 ID
         * @return 캐릭터의 환영 메시지 또는 ERROR 문자열
         */
        suspend fun getWelcomeMessage(apiKey: String, characterId: String): String = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Getting welcome message for character: $characterId using existing session")
                
                // 세션 키 (캐릭터ID:API키)
                val chatKey = "$characterId:$apiKey"
                
                // 기존 세션 활용
                val characterChat = characterChats[chatKey]
                
                if (characterChat != null && characterChat.isInitialized) {
                    // 기존 세션이 있으면 해당 세션의 프롬프트 유지하고 초기 인사말만 새로 생성
                    Log.d(TAG, "Using existing session for welcome message")
                    
                    // 모델만 가져와서 초기 인사말 생성 (세션은 건드리지 않음)
                    val generativeModel = getOrCreateModel(apiKey)
                    val characterPrompt = cachedPrompts[characterId] ?: cachedPrompts["raiden"]!!
                    val response = generativeModel.generateContent(characterPrompt)
                    val welcomeMessage = response.text ?: "안녕하세요, 여행자."
                    
                    Log.d(TAG, "Welcome message generated: ${welcomeMessage.take(30)}...")
                    return@withContext welcomeMessage
                } else {
                    // 세션이 없으면 performInitialExchange로 리디렉션
                    Log.d(TAG, "No existing session found, redirecting to performInitialExchange")
                    return@withContext performInitialExchange(apiKey, characterId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting welcome message: ${e.message}", e)
                return@withContext "ERROR"
            }
        }

        /**
         * 세션을 복원하고 이전 대화 내역을 전송합니다.
         * 이미 초기화 프롬프트가 메시지 내역에 포함되어 있으면 중복 전송하지 않도록 최적화
         *
         * @param apiKey API 키
         * @param characterId 캐릭터 ID
         * @param savedMessages 저장된 메시지 목록
         * @param forceReinitialize 세션을 강제로 재초기화할지 여부 (기본값: false)
         */
        suspend fun restoreSession(
            apiKey: String,
            characterId: String,
            savedMessages: List<Message>,
            forceReinitialize: Boolean = false
        ): Boolean = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Restoring session for character: $characterId with ${savedMessages.size} messages")
                Log.d(TAG, "Force reinitialization: $forceReinitialize")

                // 1. 복원할 메시지 필터링 - 첫번째 AI 응답은 초기화 프롬프트의 결과이므로 포함
                val messagesToRestore = savedMessages.filter { it.id != "1" }

                // 메시지가 없으면 복원 완료 (새 대화 시작)
                if (messagesToRestore.isEmpty()) {
                    // 세션만 초기화하고 종료
                    val characterChat = initializeCharacterChat(apiKey, characterId, forceReinitialize)
                    Log.d(TAG, "No messages to restore. Session initialized with persona only.")
                    return@withContext true
                }

                try {
                    // 2. 모델 가져오기
                    val model = getOrCreateModel(apiKey)

                    // 캐릭터 프롬프트 가져오기
                    val characterPrompt = cachedPrompts[characterId] ?: run {
                        // 없으면 CharacterRepository에서 가져와서 캐싱
                        val prompt = CharacterRepository.getCharacterPrompt(characterId)
                        cachedPrompts[characterId] = prompt
                        prompt
                    }

                    // 3. Chat 객체 생성 시 초기 history를 제공하기 위한 준비
                    val initialHistory = mutableListOf<Content>()

                    // 첫 번째 AI 응답 추출 (첫 인사말)
                    val firstAiMessage = savedMessages.firstOrNull { it.type == MessageType.RECEIVED }
                    
                    // 3-1. 캐릭터 프롬프트와 첫 응답이 있는 경우 (정상적인 대화 기록)
                    if (firstAiMessage != null) {
                        // 프롬프트를 사용자 메시지로, 첫 응답을 AI 응답으로 설정
                        initialHistory.add(content {
                            role = "user"
                            text(characterPrompt)
                        })
                        
                        initialHistory.add(content {
                            role = "model"
                            text(firstAiMessage.text)
                        })
                        
                        Log.d(TAG, "Added initial prompt and first AI response to history")
                    }

                    // 3-2. 나머지 메시지들 추가 (첫 인사말은 제외하고 실제 사용자-AI 대화만)
                    val actualConversation = messagesToRestore.filter { it != firstAiMessage }
                    actualConversation.forEach { message ->
                        val role = if (message.type == MessageType.SENT) "user" else "model"
                        initialHistory.add(content {
                            this.role = role
                            text(message.text)
                        })
                    }

                    // 4. 세션 초기화 및 상태 업데이트
                    val chatKey = "$characterId:$apiKey"
                    
                    if (forceReinitialize) {
                        // 기존 세션 제거
                        characterChats.remove(chatKey)
                    }

                    // 5. 초기 히스토리를 포함한 Chat 객체 생성
                    Log.d(TAG, "Creating new chat with ${initialHistory.size} history items")
                    val newChat = Chat(model, initialHistory)

                    // 6. 캐릭터 채팅 정보 업데이트
                    val updatedChatInfo = CharacterChatInfo(
                        chat = newChat,
                        apiKey = apiKey,
                        characterId = characterId,
                        isInitialized = true,
                        lastActivity = System.currentTimeMillis()
                    )

                    // 세션 캐시 업데이트
                    characterChats[chatKey] = updatedChatInfo

                    Log.d(TAG, "Session restored successfully with history using optimized method")
                    return@withContext true

                } catch (e: Exception) {
                    Log.e(TAG, "Error creating chat history: ${e.message}", e)

                    // 예외 발생 시 기존 방식으로 폴백
                    Log.d(TAG, "Falling back to manual session restoration")
                    
                    // 세션 초기화
                    val characterChat = initializeCharacterChat(apiKey, characterId, forceReinitialize)
                    
                    // 기존 방식으로 메시지 하나씩 전송 (사용자 메시지만)
                    messagesToRestore.forEach { message ->
                        try {
                            if (message.type == MessageType.SENT) {
                                characterChat.chat.sendMessage(message.text)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error restoring individual message: ${e.message}")
                            // 개별 메시지 오류는 무시하고 계속 진행
                        }
                    }

                    Log.d(TAG, "Session restored with fallback method")
                    return@withContext true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error restoring session: ${e.message}", e)
                return@withContext false
            }
        }

        /**
         * 사용자 메시지에 대한 응답을 생성합니다. (스트리밍 방식 비동기 최적화)
         */
        suspend fun generateResponseStream(
            apiKey: String,
            userMessage: String,
            chatHistory: List<Message> = emptyList(),
            characterId: String = "raiden"
        ): Flow<StreamingChatResponse> = flow {
            try {
                // 채팅 기록 로깅
                Log.d(TAG, "Generating response with ${chatHistory.size} history messages")
                
                // 병렬 작업 수행
                coroutineScope {
                    // 세션 초기화를 비동기로 시작
                    val chatJob = async(Dispatchers.IO) {
                        // 기존 세션 활용 (가능한 경우)
                        initializeCharacterChat(apiKey, characterId)
                    }

                    // 초기 응답 상태 전송
                    emit(StreamingChatResponse(isComplete = false, text = "", error = null))

                    // 세션 준비 완료 대기
                    val characterChat = chatJob.await()

                    // 사용자 메시지 전송 (스트리밍 방식)
                    Log.d(TAG, "Sending user message (stream): ${userMessage.take(30)}...")

                    // 스트리밍 응답 수집
                    val responseBuilder = StringBuilder()
                    var hasError = false

                    try {
                        // 응답 스트리밍 시작
                        characterChat.chat.sendMessageStream(userMessage).collect { chunk ->
                            chunk.text?.let { text ->
                                // 비동기로 로깅 처리 (응답 속도에 영향 없도록)
                                launch {
                                    if (responseBuilder.isEmpty()) {
                                        Log.d(TAG, "Received first streaming chunk")
                                    }
                                }

                                responseBuilder.append(text)
                                // 각 청크마다 누적된 텍스트를 Flow로 전달
                                emit(StreamingChatResponse(
                                    isComplete = false,
                                    text = responseBuilder.toString(),
                                    error = null
                                ))
                            }
                        }

                        // 응답 완료 신호
                        emit(StreamingChatResponse(
                            isComplete = true,
                            text = responseBuilder.toString(),
                            error = null
                        ))

                        // 비동기로 로깅 처리
                        launch {
                            Log.d(TAG, "Streaming response completed: ${responseBuilder.toString().take(50)}...")
                        }

                        // 여기서 채팅 기록 업데이트는 ChatScreen.kt에서 처리해야 함
                        // generateResponseStream은 단순히 응답만 반환하도록 수정

                    } catch (e: Exception) {
                        Log.e(TAG, "Error in streaming response: ${e.message}", e)
                        emit(StreamingChatResponse(
                            isComplete = true,
                            text = null,
                            error = "티바트에서 정보를 생성하던 중 오류가 발생했습니다."
                        ))
                    }

                    // 채팅 내역 저장도 ChatScreen.kt에서 처리해야 함
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating streaming response: ${e.message}", e)
                emit(StreamingChatResponse(
                    isComplete = true,
                    text = null,
                    error = "ERROR: 티바트에서 정보를 생성하던 중 오류가 발생했습니다."
                ))
            }
        }
        /**
         * 사용하지 않는 세션을 정리합니다.
         * 일정 시간 동안 사용되지 않은 세션은 제거하여 메모리를 확보합니다.
         */
        fun cleanupUnusedSessions(maxAgeMinutes: Int = 60) {
            val currentTime = System.currentTimeMillis()
            val maxAgeMillis = maxAgeMinutes * 60 * 1000L

            val sessionsToRemove = characterChats.entries
                .filter { (_, chatInfo) ->
                    (currentTime - chatInfo.lastActivity) > maxAgeMillis
                }
                .map { it.key }

            sessionsToRemove.forEach { key ->
                characterChats.remove(key)
                Log.d(TAG, "Cleaned up inactive session: $key")
            }

            Log.d(TAG, "Session cleanup completed. Removed ${sessionsToRemove.size} sessions.")
        }

        /**
         * 모든 캐릭터 세션과 초기화 상태를 초기화합니다.
         */
        fun clearAllChats() {
            characterChats.clear()
            sessionInitTasks.clear()
            // 모델 캐시는 유지 (API 키 변경 시에만 초기화)
            Log.d(TAG, "All character chats and initialization states cleared")
        }

        /**
         * 특정 캐릭터의 세션과 초기화 상태를 초기화합니다.
         */
        fun clearCharacterChat(characterId: String) {
            characterChats.entries.removeIf { it.key.startsWith("$characterId:") }
            sessionInitTasks.entries.removeIf { it.key.startsWith("$characterId:") }
            Log.d(TAG, "Chat and initialization state cleared for character: $characterId")
        }

        /**
         * API 키가 변경된 경우 모델 캐시를 초기화합니다.
         */
        fun clearModelCache() {
            generativeModels.clear()
            Log.d(TAG, "Model cache cleared")
        }

        fun transformResponseStream(stream: Flow<com.google.ai.client.generativeai.type.GenerateContentResponse>): Flow<StreamingChatResponse> {
            return stream.map { response ->
                StreamingChatResponse(
                    isComplete = false,
                    text = response.text,
                    error = null
                )
            }
        }

        // 오류 응답 생성 유틸리티 함수
        fun errorResponseFlow(errorMessage: String): Flow<StreamingChatResponse> = flow {
            emit(StreamingChatResponse(
                isComplete = true,
                text = null,
                error = errorMessage
            ))
        }
    }
}
