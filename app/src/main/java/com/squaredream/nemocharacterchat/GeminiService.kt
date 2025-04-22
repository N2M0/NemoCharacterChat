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
                // 공식 Google AI SDK를 사용하여 모델 초기화
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey
                )

                // 간단한 토큰 카운팅으로 API 키 검증 (가벼운 API 호출)
                val response = generativeModel.countTokens("Hello")

                // 응답이 성공적으로 돌아왔으면 API 키가 유효함
                Log.d(TAG, "API key validation successful. Token count: ${response.totalTokens}")
                return@withContext true
            } catch (e: Exception) {
                // 예외 발생 시 API 키가 유효하지 않거나 네트워크 문제가 있는 것
                Log.e(TAG, "API key validation failed: ${e.message}", e)
                return@withContext false
            }
        }
    }
}