package com.squaredream.nemocharacterchat

import android.app.Application
import android.util.Log
import com.squaredream.nemocharacterchat.data.GeminiSessionManager
import com.squaredream.nemocharacterchat.data.PreferencesManager

/**
 * 앱의 Application 클래스
 * 앱 전체에서 사용할 수 있는 세션 매니저를 초기화합니다.
 */
class MainApplication : Application() {

    companion object {
        private const val TAG = "MainApplication"

        // 앱 전체에서 접근 가능한 세션 매니저 인스턴스
        lateinit var sessionManager: GeminiSessionManager
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // 세션 매니저 초기화
        sessionManager = GeminiSessionManager.getInstance(applicationContext)
        Log.d(TAG, "GeminiSessionManager initialized")

        // API 키가 이미 설정되어 있다면 세션 매니저에 전달
        val preferencesManager = PreferencesManager(applicationContext)
        if (preferencesManager.isKeySet()) {
            val apiKey = preferencesManager.getApiKey()
            if (apiKey.isNotEmpty()) {
                sessionManager.initialize(apiKey)
                Log.d(TAG, "API key loaded from preferences and set in session manager")
            }
        }
    }
}