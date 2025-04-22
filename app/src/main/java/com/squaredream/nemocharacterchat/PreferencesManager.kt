package com.squaredream.nemocharacterchat.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 앱의 SharedPreferences를 관리하는 클래스
 * API 키 및 API 키 저장 상태를 관리합니다.
 */
class PreferencesManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * API 키를 SharedPreferences에 저장합니다.
     */
    fun saveApiKey(apiKey: String) {
        with(sharedPreferences.edit()) {
            putString(KEY_API_KEY, apiKey)
            apply()
        }
    }

    /**
     * 저장된 API 키를 가져옵니다.
     */
    fun getApiKey(): String {
        return sharedPreferences.getString(KEY_API_KEY, "") ?: ""
    }

    /**
     * API 키 저장 상태를 설정합니다.
     */
    fun setKeyStatus(isKeySet: Boolean) {
        with(sharedPreferences.edit()) {
            putBoolean(KEY_IS_API_KEY_SET, isKeySet)
            apply()
        }
    }

    /**
     * API 키 저장 상태를 가져옵니다.
     */
    fun isKeySet(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_API_KEY_SET, false)
    }

    companion object {
        private const val PREF_NAME = "genshin_character_chat_prefs"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_IS_API_KEY_SET = "is_api_key_set"
    }
}