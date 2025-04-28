package com.squaredream.nemocharacterchat

import android.app.Application
import android.util.Log
import com.squaredream.nemocharacterchat.data.GeminiSessionManager
import com.squaredream.nemocharacterchat.data.PreferencesManager

/**
 * 앱의 Application 클래스
 * 앱 전체에서 사용할 수 있는 세션 매니저를 초기화합니다.
 * 자동 세션 초기화는 비활성화되었습니다.
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

        // 세션 매니저 인스턴스만 초기화 (실제 세션 초기화는 하지 않음)
        sessionManager = GeminiSessionManager.getInstance(applicationContext)
        Log.d(TAG, "GeminiSessionManager instance created (session initialization disabled)")

        // 아래 자동 세션 초기화 코드는 비활성화 (주석 처리)
        /*
        // API 키가 이미 설정되어 있다면 세션 매니저에 전달
        val preferencesManager = PreferencesManager(applicationContext)
        if (preferencesManager.isKeySet()) {
            val apiKey = preferencesManager.getApiKey()
            if (apiKey.isNotEmpty()) {
                sessionManager.initialize(apiKey)
                Log.d(TAG, "API key loaded from preferences and set in session manager")
            }
        }
        */
    }
}