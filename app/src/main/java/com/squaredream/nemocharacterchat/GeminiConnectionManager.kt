package com.squaredream.nemocharacterchat.data

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

// 연결 상태를 나타내는 sealed class
sealed class ConnectionStatus {
    object Idle : ConnectionStatus()
    object Loading : ConnectionStatus()
    object Connected : ConnectionStatus()
    data class Error(val message: String) : ConnectionStatus()
}

/**
 * Gemini API 연결을 앱 전체에서 관리하는 싱글톤 객체
 */
object GeminiConnectionManager {
    private const val TAG = "GeminiConnectionManager"
    private const val MODEL_NAME = "gemini-2.5-flash-preview-04-17"

    private var _generativeModel: GenerativeModel? = null
    val generativeModel: GenerativeModel? get() = _generativeModel

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Idle)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private var isInitializing = false

    /**
     * Gemini API 연결을 초기화합니다.
     * 이미 초기화 중이거나 완료된 경우 다시 초기화하지 않습니다.
     */
    suspend fun initialize(context: Context, apiKey: String) {
        if (isInitializing || _connectionStatus.value == ConnectionStatus.Connected) {
            Log.d(TAG, "Initialization already in progress or connected.")
            return
        }

        isInitializing = true
        _connectionStatus.value = ConnectionStatus.Loading
        Log.d(TAG, "Starting Gemini connection initialization.")

        try {
            // GenerativeModel 인스턴스 생성
            val model = GenerativeModel(
                modelName = MODEL_NAME,
                apiKey = apiKey
            )
            _generativeModel = model

            // 간단한 테스트 호출로 연결 확인 (선택 사항)
            // val response = model.generateContent("Hello")
            // Log.d(TAG, "Connection test successful.")

            _connectionStatus.value = ConnectionStatus.Connected
            Log.d(TAG, "Gemini connection established successfully.")

        } catch (e: Exception) {
            _generativeModel = null
            _connectionStatus.value = ConnectionStatus.Error("API 연결에 실패했습니다: ${e.message}")
            Log.e(TAG, "Gemini connection initialization failed: ${e.message}", e)
        } finally {
            isInitializing = false
        }
    }

    /**
     * 연결 상태를 초기 상태로 재설정합니다.
     * (앱 종료 시나 특정 상황에 호출 가능)
     */
    fun resetConnection() {
        _generativeModel = null
        _connectionStatus.value = ConnectionStatus.Idle
        isInitializing = false
        Log.d(TAG, "Gemini connection state reset.")
    }

    /**
     * 현재 연결이 유효한지 확인합니다.
     */
    fun isConnected(): Boolean {
        return _connectionStatus.value == ConnectionStatus.Connected && _generativeModel != null
    }
}