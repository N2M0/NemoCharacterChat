package com.squaredream.nemocharacterchat.data

/**
 * 채팅방 정보를 담는 데이터 클래스
 *
 * @param id 채팅방 고유 ID
 * @param name 캐릭터 이름
 * @param lastMessage 마지막 메시지
 * @param time 마지막 메시지 시간
 * @param profileImage 프로필 이미지 리소스 ID
 */
data class ChatRoom(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val profileImage: Int
)