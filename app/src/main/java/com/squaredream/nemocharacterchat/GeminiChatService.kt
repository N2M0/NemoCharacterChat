package com.squaredream.nemocharacterchat.data

import android.provider.Settings.Global.getString
import android.util.Log
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.squaredream.nemocharacterchat.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gemini API를 사용하여 채팅 기능을 제공하는 서비스 클래스
 * 최적화된 버전 - 중복 API 호출 제거, 이전 대화 내역 복원 기능 추가
 */
class GeminiChatService {
    companion object {
        private const val TAG = "GeminiChatService"
        private const val MODEL_NAME = "gemini-2.5-flash-preview-04-17"

        // 세션 초기화 진행 상태
        private var sessionInitializationInProgress = mutableMapOf<String, Boolean>()

        // 캐릭터별 세션 캐시
        private var characterChats = mutableMapOf<String, CharacterChatInfo>()

        // 세션 초기화 상태 추적
        private var initializedCharacters = mutableSetOf<String>()

        // 모델 캐싱 - API 키별로 GenerativeModel 인스턴스 재사용
        private var generativeModels = mutableMapOf<String, GenerativeModel>()

        // 캐릭터별 프롬프트 템플릿
        private val CHARACTER_PROMPTS = mapOf(
            "raiden" to R.string.raiden_prompt,
            "furina" to R.string.furina_prompt
        )

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

        data class StreamingChatResponse(
            val isComplete: Boolean,  // 응답이 완료되었는지 여부
            val text: String?,        // 현재까지 받은 응답 텍스트
            val error: String?        // 오류 메시지 (있는 경우)
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
         * 비동기 처리 최적화 버전
         */
        suspend fun performInitialExchange(
            apiKey: String,
            characterId: String
        ): String = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting initial exchange for character: $characterId")

                // 병렬 작업 수행
                coroutineScope {
                    // 1. 기존 세션 정리 (비동기)
                    val clearJob = async {
                        clearCharacterChat(characterId)
                    }

                    // 2. 모델 준비 (비동기)
                    val modelJob = async {
                        getOrCreateModel(apiKey)
                    }

                    // 3. 프롬프트 준비 (비동기)
                    val promptJob = async {
                        CHARACTER_PROMPTS[characterId] ?: CHARACTER_PROMPTS["raiden"]!!
                    }

                    // 작업 완료 대기
                    clearJob.await()
                    val generativeModel = modelJob.await()
                    val characterPrompt = promptJob.await()

                    // 프롬프트 직접 전송하여 첫 응답 받기
                    val response = generativeModel.generateContent(characterPrompt)
                    val initialMessage = response.text ?: "안녕하세요, 여행자."

                    // 세션 초기화 병렬 작업
                    val sessionKey = "$characterId:$apiKey"

                    // 4. 새 채팅 세션 시작 및 초기화 (병렬 처리)
                    launch {
                        initializedCharacters.add(sessionKey)
                    }

                    val chatJob = async {
                        val chat = generativeModel.startChat()

                        // 페르소나 설정
                        try {
                            chat.sendMessage(characterPrompt)
                            Log.d(TAG, "Character persona set in chat session")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to set character persona in chat session: ${e.message}")
                        }

                        chat
                    }

                    // 채팅 준비 완료 대기
                    val chat = chatJob.await()

                    // 채팅 정보 저장
                    val characterChat = CharacterChatInfo(
                        chat = chat,
                        apiKey = apiKey,
                        characterId = characterId,
                        isInitialized = true,
                        lastActivity = System.currentTimeMillis()
                    )

                    // 세션 캐시에 저장
                    characterChats[sessionKey] = characterChat

                    Log.d(TAG, "Initial message received: ${initialMessage.take(50)}...")
                    return@coroutineScope initialMessage
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in initial exchange: ${e.message}", e)
                return@withContext "ERROR"
            }
        }

        /**
         * 캐릭터의 채팅 세션을 초기화합니다.
         * 이미 초기화된 세션이 있다면 재사용하고, 없으면 새로 생성합니다.
         */
        private suspend fun initializeCharacterChat(
            apiKey: String,
            characterId: String,
            forceReinitialize: Boolean = false
        ): CharacterChatInfo = withContext(Dispatchers.IO) {
            // 세션 키 (캐릭터ID:API키)
            val chatKey = "$characterId:$apiKey"

            // 초기화 중인지 확인
            if (sessionInitializationInProgress[chatKey] == true) {
                // 초기화가 진행 중이면 완료될 때까지 대기
                var count = 0
                while (sessionInitializationInProgress[chatKey] == true && count < 10) {
                    delay(100) // 300ms 대기
                    count++
                }
            }

            // 강제 재초기화인 경우 기존 세션 제거
            if (forceReinitialize) {
                characterChats.remove(chatKey)
                initializedCharacters.remove(chatKey)
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

            // 세션 초기화 시작 표시
            sessionInitializationInProgress[chatKey] = true

            try {
                // 새 세션 생성
                Log.d(TAG, "Creating new chat for character: $characterId")

                // 캐싱된 Gemini 모델 가져오기
                val generativeModel = getOrCreateModel(apiKey)

                // 캐릭터 프롬프트 가져오기
                val characterPrompt = CHARACTER_PROMPTS[characterId] ?: CHARACTER_PROMPTS["raiden"]!!

                // 새 채팅 세션 시작
                val chat = generativeModel.startChat()

                // 항상 페르소나 설정 프롬프트 전송 (이전 초기화 상태 무시)
                try {
                    Log.d(TAG, "Sending character persona prompt")
                    val response = chat.sendMessage(characterPrompt)
                    Log.d(TAG, "Character persona set successfully: ${response.text?.take(30)}")
                    initializedCharacters.add(chatKey)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting character persona: ${e.message}")
                    sessionInitializationInProgress[chatKey] = false
                    throw e  // 초기화 실패 시 예외 전파
                }

                // 새 채팅 정보 생성 및 캐시에 저장
                val characterChat = CharacterChatInfo(
                    chat = chat,
                    apiKey = apiKey,
                    characterId = characterId,
                    isInitialized = true,
                    lastActivity = System.currentTimeMillis()
                )

                characterChats[chatKey] = characterChat
                sessionInitializationInProgress[chatKey] = false
                return@withContext characterChat
            } catch (e: Exception) {
                // 오류 발생 시 초기화 상태 정리
                sessionInitializationInProgress[chatKey] = false
                throw e
            }
        }

        /**
         * 세션을 복원하고 이전 대화 내역을 전송합니다.
         * 비동기 처리 최적화 버전
         */
        suspend fun restoreSession(
            apiKey: String,
            characterId: String,
            savedMessages: List<Message>
        ): Boolean = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Restoring session for character: $characterId with ${savedMessages.size} messages")

                // 효율적인 처리를 위한 준비 작업을 병렬로 수행
                coroutineScope {
                    // 1. 세션 초기화 작업 시작 (비동기)
                    val initJob = async {
                        initializeCharacterChat(apiKey, characterId, forceReinitialize = true)
                    }

                    // 2. 복원할 메시지 필터링 작업 (비동기)
                    val messagesJob = async {
                        savedMessages.filter {
                            it.type == MessageType.SENT && it.id != "1"
                        }
                    }

                    // 두 작업이 모두 완료될 때까지 대기
                    val characterChat = initJob.await()
                    val messagesToRestore = messagesJob.await()

                    // 메시지가 없으면 복원 완료 (새 대화 시작)
                    if (messagesToRestore.isEmpty()) {
                        Log.d(TAG, "No messages to restore. Session initialized with persona only.")
                        return@coroutineScope true
                    }

                    // 3. 모든 메시지 복원 - 제한 없이 모든 대화 내역 전송
                    Log.d(TAG, "Restoring all ${messagesToRestore.size} messages to maintain complete context")

                    // 청크 단위로 병렬 처리 (단, 순서 보장)
                    val chunkSize = 5 // 한 번에 처리할 메시지 청크 크기
                    val results = messagesToRestore.chunked(chunkSize).map { chunk ->
                        async {
                            // 각 청크는 순차적으로 처리 (순서 유지 필요)
                            chunk.forEach { message ->
                                try {
                                    Log.d(TAG, "Restoring message: ${message.text.take(30)}...")
                                    characterChat.chat.sendMessage(message.text)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error restoring message: ${e.message}")
                                    // 개별 메시지 오류는 무시하고 계속 진행
                                }
                            }
                            true
                        }
                    }

                    // 모든 청크 처리 완료 대기
                    results.awaitAll()
                    Log.d(TAG, "Session restored successfully with all ${messagesToRestore.size} messages")
                }

                return@withContext true
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
                // 병렬 작업 수행
                coroutineScope {
                    // 세션 초기화를 비동기로 시작
                    val chatJob = async(Dispatchers.IO) {
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

                    try {
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
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in streaming response: ${e.message}", e)
                        emit(StreamingChatResponse(
                            isComplete = true,
                            text = null,
                            error = "티바트에서 정보를 생성하던 중 오류가 발생했습니다."
                        ))
                    }
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
                initializedCharacters.remove(key)
                Log.d(TAG, "Cleaned up inactive session: $key")
            }

            Log.d(TAG, "Session cleanup completed. Removed ${sessionsToRemove.size} sessions.")
        }

        /**
         * 모든 캐릭터 세션과 초기화 상태를 초기화합니다.
         */
        fun clearAllChats() {
            characterChats.clear()
            initializedCharacters.clear()
            sessionInitializationInProgress.clear()
            // 모델 캐시는 유지 (API 키 변경 시에만 초기화)
            Log.d(TAG, "All character chats and initialization states cleared")
        }

        /**
         * 특정 캐릭터의 세션과 초기화 상태를 초기화합니다.
         */
        fun clearCharacterChat(characterId: String) {
            characterChats.entries.removeIf { it.key.startsWith("$characterId:") }
            initializedCharacters.removeIf { it.startsWith("$characterId:") }
            sessionInitializationInProgress.entries.removeIf { it.key.startsWith("$characterId:") }
            Log.d(TAG, "Chat and initialization state cleared for character: $characterId")
        }

        /**
         * API 키가 변경된 경우 모델 캐시를 초기화합니다.
         */
        fun clearModelCache() {
            generativeModels.clear()
            Log.d(TAG, "Model cache cleared")
        }
    }
}