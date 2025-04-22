package com.squaredream.nemocharacterchat.data

/**
 * 채팅 메시지 타입
 */
enum class MessageType {
    SENT,      // 내가 보낸 메시지
    RECEIVED   // 상대방이 보낸 메시지
}

/**
 * 채팅 메시지 정보를 담는 데이터 클래스
 *
 * @param id 메시지 고유 ID
 * @param text 메시지 텍스트
 * @param timestamp 메시지 타임스탬프 (표시용 문자열)
 * @param type 메시지 타입 (보낸 메시지 또는 받은 메시지)
 * @param sender 메시지 발신자
 */
data class Message(
    val id: String,
    val text: String,
    val timestamp: String,
    val type: MessageType,
    val sender: String
)