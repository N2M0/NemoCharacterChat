package com.squaredream.nemocharacterchat.data

import com.squaredream.nemocharacterchat.data.Message
import com.squaredream.nemocharacterchat.data.MessageType

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/**
 * 채팅 내역을 저장하고 불러오는 관리자 클래스
 */
class ChatHistoryManager(private val context: Context) {

    companion object {
        private const val TAG = "ChatHistoryManager"
        private const val HISTORY_DIRECTORY = "chat_histories"
        private const val MAX_MESSAGES_PER_CHAT = 100 // 채팅방당 최대 저장 메시지 수
    }

    /**
     * 채팅 내역 객체 - 직렬화 가능하도록 설계
     */
    data class ChatHistory(
        val characterId: String,
        val messages: MutableList<SerializableMessage> = mutableListOf()
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * 직렬화 가능한 메시지 클래스
     */
    data class SerializableMessage(
        val id: String,
        val text: String,
        val timestamp: String,
        val type: Int, // MessageType enum의 ordinal 값 저장
        val sender: String
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L

            // Message 객체를 SerializableMessage로 변환
            fun fromMessage(message: Message): SerializableMessage {
                return SerializableMessage(
                    id = message.id,
                    text = message.text,
                    timestamp = message.timestamp,
                    type = message.type.ordinal,
                    sender = message.sender
                )
            }
        }

        // SerializableMessage를 Message로 변환
        fun toMessage(): Message {
            return Message(
                id = id,
                text = text,
                timestamp = timestamp,
                type = MessageType.values()[type],
                sender = sender
            )
        }
    }

    /**
     * 채팅 내역 저장 디렉토리 초기화
     */
    private fun initHistoryDirectory(): File {
        val directory = File(context.filesDir, HISTORY_DIRECTORY)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    /**
     * 캐릭터별 채팅 내역 파일 얻기
     */
    private fun getHistoryFile(characterId: String): File {
        val directory = initHistoryDirectory()
        return File(directory, "${characterId}_history.dat")
    }

    /**
     * 채팅 내역 저장
     */
    suspend fun saveChatHistory(characterId: String, messages: List<Message>) = withContext(Dispatchers.IO) {
        try {
            val file = getHistoryFile(characterId)

            // 메시지를 직렬화 가능한 형태로 변환
            val serializableMessages = messages.map { SerializableMessage.fromMessage(it) }

            // 저장할 채팅 내역 객체 생성
            val chatHistory = ChatHistory(
                characterId = characterId,
                messages = serializableMessages.takeLast(MAX_MESSAGES_PER_CHAT).toMutableList()
            )

            // 파일에 저장
            ObjectOutputStream(file.outputStream()).use { out ->
                out.writeObject(chatHistory)
            }

            Log.d(TAG, "Saved ${chatHistory.messages.size} messages for character: $characterId")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat history: ${e.message}", e)
        }
    }

    /**
     * 채팅 내역 불러오기
     */
    suspend fun loadChatHistory(characterId: String): List<Message> = withContext(Dispatchers.IO) {
        try {
            val file = getHistoryFile(characterId)

            if (!file.exists()) {
                Log.d(TAG, "No chat history found for character: $characterId")
                return@withContext emptyList<Message>()
            }

            // 파일에서 채팅 내역 읽기
            val chatHistory = ObjectInputStream(file.inputStream()).use { input ->
                input.readObject() as ChatHistory
            }

            // 직렬화된 메시지를 원래 Message 객체로 변환
            val messages = chatHistory.messages.map { it.toMessage() }

            Log.d(TAG, "Loaded ${messages.size} messages for character: $characterId")
            return@withContext messages
        } catch (e: Exception) {
            Log.e(TAG, "Error loading chat history: ${e.message}", e)
            return@withContext emptyList<Message>()
        }
    }

    /**
     * 모든 채팅 내역 삭제
     */
    suspend fun clearAllHistories() = withContext(Dispatchers.IO) {
        try {
            val directory = initHistoryDirectory()
            directory.listFiles()?.forEach { it.delete() }
            Log.d(TAG, "All chat histories cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing chat histories: ${e.message}", e)
        }
    }

    /**
     * 특정 캐릭터의 채팅 내역 삭제
     */
    suspend fun clearChatHistory(characterId: String) = withContext(Dispatchers.IO) {
        try {
            val file = getHistoryFile(characterId)
            if (file.exists()) {
                file.delete()
                Log.d(TAG, "Chat history cleared for character: $characterId")
            } else {

            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing chat history: ${e.message}", e)
        }
    }
}