package com.squaredream.nemocharacterchat.data

/**
 * 채팅할 캐릭터 정보를 담는 데이터 클래스
 *
 * @param id 캐릭터 고유 ID
 * @param name 캐릭터 이름
 * @param profileImage 프로필 이미지 리소스 ID
 */
data class Character(
    val id: String,
    val name: String,
    val profileImage: Int
)