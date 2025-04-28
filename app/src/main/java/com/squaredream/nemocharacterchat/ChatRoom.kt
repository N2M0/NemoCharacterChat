package com.squaredream.nemocharacterchat.data

/**
 * 채팅방 정보를 담는 데이터 클래스
 *
 * @param characterId 캐릭터 고유 ID
 * @param lastMessage 마지막 메시지
 * @param time 마지막 메시지 시간
 */
data class ChatRoom(
    val characterId: String,
    val lastMessage: String,
    val time: String
) {
    /**
     * 채팅방에 해당하는 캐릭터 정보를 반환합니다.
     */
    fun getCharacter(): Character? {
        return CharacterRepository.getCharacterById(characterId)
    }
}