package com.squaredream.nemocharacterchat.data

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini API와의 통신을 담당하는 서비스 클래스
 */
class GeminiService {
    companion object {
        private const val TAG = "GeminiService"

        /**
         * API 키가 유효한지 테스트합니다.
         * 공식 Google AI SDK를 사용하여 간단한 요청을 보냅니다.
         *
         * @param apiKey 테스트할 API 키
         * @return 키가 유효하면 true, 아니면 false
         */
        suspend fun testApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
            try {
                // 가벼운 요청으로 API 키 검증
                return@withContext testLightRequest(apiKey)
            } catch (e: Exception) {
                // 예외 발생 시 API 키가 유효하지 않거나 네트워크 문제가 있는 것
                Log.e(TAG, "API key validation failed: ${e.message}", e)
                return@withContext false
            }
        }

        /**
         * 매우 가벼운 API 요청을 보내 API 키가 유효한지 빠르게 확인합니다.
         */
        private suspend fun testLightRequest(apiKey: String): Boolean = withContext(Dispatchers.IO) {
            try {
                // 가장 짧은 가능한 요청 (단일 문자)
                val model = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey
                )
                model.generateContent(".")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Light API test failed: ${e.message}")
                return@withContext false
            }
        }
    }
}