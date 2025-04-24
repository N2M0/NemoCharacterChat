package com.squaredream.nemocharacterchat.data

import android.content.Context
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
 * GeminiConnectionManager를 통해 GenerativeModel을 공유하여 사용합니다.
 * 세션 초기화 및 복원 로직을 첫 메시지 전송 시 수행하도록 변경합니다.
 */
class GeminiChatService {
    companion object {
        private const val TAG = "GeminiChatService"
        // MODEL_NAME은 이제 GeminiConnectionManager에서 관리합니다.
        // private const val MODEL_NAME = "gemini-2.5-flash-preview-04-17"

        // 캐릭터별 세션 캐시
        private val characterChats = mutableMapOf<String, CharacterChatInfo>()

        // 캐릭터별 프롬프트 템플릿 리소스 ID 맵 (직접 문자열 대신 리소스 ID 저장)
        private val CHARACTER_PROMPT_RES_IDS = mapOf(
            "raiden" to R.string.raiden_prompt,
            "furina" to R.string.furina_prompt
        )

        /**
         * 캐릭터 채팅 정보를 저장하는 데이터 클래스
         */
        private data class CharacterChatInfo(
            val chat: Chat,              // Gemini 채팅 객체
            val characterId: String,     // 캐릭터 ID
            var isPersonaSet: Boolean,   // 페르소나 설정 완료 여부
            var lastActivity: Long
        )

        data class StreamingChatResponse(
            val isComplete: Boolean,  // 응답이 완료되었는지 여부
            val text: String?,        // 현재까지 받은 응답 텍스트
            val error: String?        // 오류 메시지 (있는 경우)
        )

        /**
         * 리소스 ID를 사용하여 캐릭터 프롬프트 문자열을 가져옵니다.
         */
        private fun getCharacterPrompt(context: Context, characterId: String): String {
            val promptResId = CHARACTER_PROMPT_RES_IDS[characterId] ?: R.string.raiden_prompt // Default to Raiden if not found
            return context.getString(promptResId)
        }

        /**
         * 캐릭터의 채팅 세션을 초기화하거나 재사용합니다.
         * 이 함수는 세션 객체 자체를 관리하며, 페르소나 설정 여부 상태를 포함합니다.
         *
         * @param context Context
         * @param characterId 캐릭터 ID
         * @return 캐릭터 채팅 정보 (CharacterChatInfo)
         * @throws IllegalStateException GenerativeModel이 초기화되지 않은 경우 발생
         */
        private suspend fun getOrCreateCharacterChat(
            context: Context,
            characterId: String
        ): CharacterChatInfo = withContext(Dispatchers.IO) {
            // GeminiConnectionManager로부터 GenerativeModel 인스턴스를 가져옴
            val generativeModel = GeminiConnectionManager.generativeModel
                ?: throw IllegalStateException("GenerativeModel is not initialized in ConnectionManager.")

            // 세션 키는 캐릭터 ID만 사용합니다.
            val chatKey = characterId

            // 기존 세션이 있는지 확인하고 재사용
            characterChats[chatKey]?.let { existingChat ->
                // 마지막 활동 시간 업데이트
                existingChat.lastActivity = System.currentTimeMillis()
                Log.d(TAG, "Reusing existing chat for character: $characterId")
                return@withContext existingChat
            }

            // 기존 세션이 없으면 새 세션 생성
            Log.d(TAG, "Creating new chat for character: $characterId")

            // 새 채팅 세션 시작 (GenerativeModel은 ConnectionManager에서 가져온 것을 사용)
            val chat = generativeModel.startChat()

            // 새 채팅 정보 생성 및 캐시에 저장
            val characterChat = CharacterChatInfo(
                chat = chat,
                characterId = characterId,
                isPersonaSet = false, // 새 세션이므로 페르소나 설정 필요
                lastActivity = System.currentTimeMillis()
            )

            characterChats[chatKey] = characterChat
            Log.d(TAG, "New chat created and cached for character: $characterId")

            return@withContext characterChat
        }


        /**
         * 첫 메시지 전송 시 호출되어 세션을 준비(생성/복원/페르소나 설정)하고 응답을 생성합니다.
         * 이 함수는 해당 캐릭터와의 첫 API 상호작용 시점에 호출됩니다.
         *
         * @param context Context
         * @param characterId 캐릭터 ID
         * @param userMessage 사용자가 보낸 첫 메시지
         * @param savedMessagesIfAny 로컬 저장소에서 불러온 이전 대화 내역 (사용자 메시지)
         * @return 응답 스트림 Flow
         */
        suspend fun prepareChatSessionAndGenerateResponse(
            context: Context,
            characterId: String,
            userMessage: String,
            savedMessagesIfAny: List<Message> // ChatScreen에서 로드한 사용자 메시지만 필터링하여 전달
        ): Flow<StreamingChatResponse> = flow {
            // GeminiConnectionManager로부터 GenerativeModel 인스턴스를 가져옴
            val generativeModel = GeminiConnectionManager.generativeModel
                ?: run {
                    emit(StreamingChatResponse(
                        isComplete = true,
                        text = null,
                        error = "ERROR: API 연결이 되어있지 않아 메시지를 보낼 수 없습니다."
                    ))
                    return@flow
                }

            try {
                // 해당 캐릭터의 채팅 세션을 가져오거나 초기화
                val characterChatInfo = getOrCreateCharacterChat(context, characterId)
                val chat = characterChatInfo.chat

                // Persona 설정 및 대화 내역 복원 (아직 설정되지 않은 경우에만)
                if (!characterChatInfo.isPersonaSet) {
                    Log.d(TAG, "Persona not set for character $characterId. Setting now and restoring history if available.")

                    // 1단계: 페르소나 프롬프트 전송
                    val characterPrompt = getCharacterPrompt(context, characterId)
                    try {
                        Log.d(TAG, "Sending persona prompt for $characterId")
                        chat.sendMessage(characterPrompt) // 페르소나 설정 프롬프트 전송
                        characterChatInfo.isPersonaSet = true // 페르소나 설정 완료 상태 업데이트
                        Log.d(TAG, "Persona prompt sent and marked as set.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending persona prompt during preparation: ${e.message}")
                        emit(StreamingChatResponse(isComplete = true, text = null, error = "ERROR: 캐릭터 설정을 실패했습니다."))
                        return@flow // 오류 발생 시 중단
                    }

                    // 2단계: 저장된 사용자 메시지 전송하여 컨텍스트 복원
                    if (savedMessagesIfAny.isNotEmpty()) {
                        Log.d(TAG, "Restoring saved user messages for $characterId: ${savedMessagesIfAny.size} messages")
                        // savedMessagesIfAny는 이미 ChatScreen에서 타입이 SENT인 메시지만 필터링해서 전달한다고 가정합니다.
                        val messagesToRestore = savedMessagesIfAny

                        // 순서 보장을 위해 청크 단위 순차 처리
                        val chunkSize = 5
                        messagesToRestore.chunked(chunkSize).forEach { chunk ->
                            chunk.forEach { message ->
                                try {
                                    Log.d(TAG, "Restoring message: ${message.text.take(30)}...")
                                    // 메시지를 보내면서 Chat 객체의 내부 history 업데이트
                                    // 주의: 복원 메시지는 사용자 메시지만 보내야 합니다. (ChatScreen에서 이미 필터링)
                                    chat.sendMessage(message.text)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error restoring message ${message.id} during preparation: ${e.message}")
                                    // 개별 메시지 복원 오류는 무시하고 계속 진행
                                }
                            }
                            delay(50) // 각 청크 처리 후 약간의 딜레이
                        }
                        Log.d(TAG, "Saved user messages restoration attempted.")
                    }
                } else {
                    // 이미 페르소나 설정이 완료된 세션인 경우 (캐시된 세션 재사용 등)
                    Log.d(TAG, "Session for character $characterId is already prepared (persona set).")
                    // 이 경우는 savedMessagesIfAny가 비어있어야 정상입니다. (첫 로딩 시 restoreSession을 거치지 않으므로)
                    // 만약 savedMessagesIfAny가 비어있지 않다면, 이는 예상치 못한 상황이거나
                    // 세션은 캐시되었지만 내부 history가 날아간 경우일 수 있습니다.
                    // 현재 로직에서는 isPersonaSet만 보고 판단하므로, 캐시된 세션은 history가 유지된다고 가정합니다.
                }

                // 초기 응답 상태 전송 (사용자 메시지에 대한 실제 응답 스트림 시작 전)
                emit(StreamingChatResponse(isComplete = false, text = "", error = null))

                // 사용자 메시지 전송 (스트리밍 방식)
                Log.d(TAG, "Sending user message (stream) for character ${characterId}: ${userMessage.take(30)}...")

                // 스트리밍 응답 수집
                val responseBuilder = StringBuilder()

                try {
                    // sendMessageStream 호출 시, getOrCreateCharacterChat에서 가져온 기존 chat 객체를 사용
                    chat.sendMessageStream(userMessage).collect { chunk ->
                        chunk.text?.let { text ->
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

                    // 마지막 활동 시간 업데이트
                    characterChatInfo.lastActivity = System.currentTimeMillis()

                    Log.d(TAG, "Streaming response completed after preparation for character ${characterId}.")

                } catch (e: Exception) {
                    Log.e(TAG, "Error in streaming response after preparation for character ${characterId}: ${e.message}", e)
                    emit(StreamingChatResponse(
                        isComplete = true,
                        text = null,
                        error = "티바트에서 정보를 생성하던 중 오류가 발생했습니다."
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing chat session and generating response for character ${characterId}: ${e.message}", e)
                emit(StreamingChatResponse(
                    isComplete = true,
                    text = null,
                    error = "ERROR: 대화 세션을 준비하는 중 오류가 발생했습니다."
                ))
            }
        }


        /**
         * 준비된 세션에 사용자 메시지를 보내고 응답을 생성합니다. (스트리밍 방식)
         * 이 함수는 prepareChatSessionAndGenerateResponse 호출 이후, 세션이 준비된 상태에서 호출됩니다.
         *
         * @param userMessage 사용자 메시지
         * @param characterId 캐릭터 ID
         * @return 응답 스트림 Flow
         */
        suspend fun generateResponseStream(
            userMessage: String,
            characterId: String
            // Context는 더 이상 필요 없습니다. GenerativeModel은 ConnectionManager에서, 프롬프트는 Companion Object에서 가져옵니다.
        ): Flow<StreamingChatResponse> = flow {
            // GeminiConnectionManager로부터 GenerativeModel 인스턴스를 가져옴
            // 이 시점에는 GenerativeModel이 초기화되어 있어야 합니다.
            val generativeModel = GeminiConnectionManager.generativeModel
                ?: run {
                    // 이론적으로 prepareChatSessionAndGenerateResponse가 먼저 성공했다면 이 에러는 발생하지 않아야 합니다.
                    // 안전을 위한 방어 코드입니다.
                    Log.e(TAG, "GenerativeModel is null during subsequent message send.")
                    emit(StreamingChatResponse(
                        isComplete = true,
                        text = null,
                        error = "ERROR: API 연결 상태가 불안정합니다. 앱을 다시 시작해주세요."
                    ))
                    return@flow
                }


            try {
                // 해당 캐릭터의 기존 채팅 세션을 캐시에서 가져옵니다.
                val characterChatInfo = characterChats[characterId]
                    ?: run {
                        // prepareChatSessionAndGenerateResponse가 먼저 호출되어 세션이 생성/로딩되었다고 가정합니다.
                        // 만약 세션이 캐시에 없다면 비정상 상태입니다.
                        Log.e(TAG, "Attempted to generate response for character $characterId but session not found in cache.")
                        emit(StreamingChatResponse(
                            isComplete = true,
                            text = null,
                            error = "ERROR: 대화 세션이 비정상적으로 종료되었습니다. 새로운 대화를 시작해 주세요."
                        ))
                        return@flow
                    }

                val chat = characterChatInfo.chat

                // 이 함수 호출 시점에는 prepareChatSessionAndGenerateResponse를 통해 isPersonaSet이 true라고 가정합니다.
                // 여기서 다시 isPersonaSet을 확인하고 프롬프트를 보내면 중복됩니다.

                // 초기 응답 상태 전송
                emit(StreamingChatResponse(isComplete = false, text = "", error = null))

                // 사용자 메시지 전송 (스트리밍 방식)
                Log.d(TAG, "Sending user message (stream) for character ${characterId}: ${userMessage.take(30)}...")

                // 스트리밍 응답 수집
                val responseBuilder = StringBuilder()

                try {
                    // sendMessageStream 호출 시, 캐시된 chat 객체를 사용
                    chat.sendMessageStream(userMessage).collect { chunk ->
                        chunk.text?.let { text ->
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

                    // 마지막 활동 시간 업데이트
                    characterChatInfo.lastActivity = System.currentTimeMillis()

                    Log.d(TAG, "Streaming response completed for subsequent message for character ${characterId}.")

                } catch (e: Exception) {
                    Log.e(TAG, "Error in streaming response for character ${characterId}: ${e.message}", e)
                    emit(StreamingChatResponse(
                        isComplete = true,
                        text = null,
                        error = "티바트에서 정보를 생성하던 중 오류가 발생했습니다."
                    ))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating streaming response (getting session failed) for character ${characterId}: ${e.message}", e)
                emit(StreamingChatResponse(
                    isComplete = true,
                    text = null,
                    error = "ERROR: 대화 세션을 가져오는 중 오류가 발생했습니다."
                ))
            }
        }


        /**
         * 사용하지 않는 세션을 정리합니다.
         * 일정 시간 동안 사용되지 않은 세션은 제거하여 메모리를 확보합니다.
         * 이 함수는 필요에 따라 주기적으로 호출되어야 합니다. (MainActivity에서 호출 중)
         */
        fun cleanupUnusedSessions(maxAgeMinutes: Int = 60) {
            val currentTime = System.currentTimeMillis()
            val maxAgeMillis = maxAgeMinutes * 60 * 1000L

            val sessionsToRemoveKeys = characterChats.entries
                .filter { (_, chatInfo) ->
                    (currentTime - chatInfo.lastActivity) > maxAgeMillis
                }
                .map { it.key }

            sessionsToRemoveKeys.forEach { key ->
                characterChats.remove(key)
                // 초기화 상태 추적 (initializedCharacters) 변수는 더 이상 사용하지 않습니다.
                // initializedCharacters.remove(key)
                Log.d(TAG, "Cleaned up inactive session: $key")
            }

            Log.d(TAG, "Session cleanup completed. Removed ${sessionsToRemoveKeys.size} sessions.")
        }

        /**
         * 모든 캐릭터 세션 캐시를 초기화합니다.
         */
        fun clearAllCharacterChatCache() {
            characterChats.clear()
            // initializedCharacters.clear() // 이 변수는 이제 사용하지 않음
            Log.d(TAG, "All character chat sessions cleared from cache")
        }

        /**
         * 특정 캐릭터의 세션을 캐시에서만 제거합니다. (실제 대화 초기화와 분리)
         * 이 함수는 ChatScreen에서 '새 채팅 시작' 시 호출됩니다.
         */
        fun clearCharacterChatCache(characterId: String) {
            val removed = characterChats.remove(characterId)
            // initializedCharacters.remove(characterId) // 이 변수는 이제 사용하지 않음
            if (removed != null) {
                Log.d(TAG, "Chat session removed from cache for character: $characterId")
            } else {
                Log.d(TAG, "Attempted to clear chat cache for character $characterId, but no session was found.")
            }
        }

        // performInitialExchange 함수 제거
        // restoreSession 함수 제거
        // clearCharacterChatAndInitialize 함수 제거
    }
}