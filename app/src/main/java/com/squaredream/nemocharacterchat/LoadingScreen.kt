package com.squaredream.nemocharacterchat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.squaredream.nemocharacterchat.data.ConnectionStatus
import com.squaredream.nemocharacterchat.data.GeminiConnectionManager
import com.squaredream.nemocharacterchat.data.PreferencesManager
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay // 코루틴 delay 함수 사용
import androidx.compose.ui.text.style.TextAlign // TextAlign 임포트

@Composable
fun LoadingScreen(navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val context = LocalContext.current
        val preferencesManager = remember { PreferencesManager(context) }
        val connectionStatus by GeminiConnectionManager.connectionStatus.collectAsState()

        // 로딩 UI를 표시할지 여부를 결정하는 상태 (빠른 로딩 시 깜빡임 방지)
        var showLoadingUI by remember { mutableStateOf(false) }

        // 일정 시간 후 로딩 UI 표시
        LaunchedEffect(connectionStatus) {
            // 상태가 Loading으로 바뀌면 500ms 후에 showLoadingUI를 true로 설정
            if (connectionStatus == ConnectionStatus.Loading) {
                delay(500) // 0.5초 지연
                if (connectionStatus == ConnectionStatus.Loading) { // 지연 후에도 여전히 Loading 상태인지 확인
                    showLoadingUI = true
                }
            } else {
                // Loading 상태가 아니면 로딩 UI 숨김 (Connected, Idle, Error)
                showLoadingUI = false
            }
        }


        // 연결 시작 로직
        LaunchedEffect(Unit) {
            // 상태가 Idle 또는 Error일 때만 초기화 로직을 실행합니다.
            // 이미 Loading 또는 Connected 상태인 경우에는 초기화를 다시 시작하지 않습니다.
            if (connectionStatus == ConnectionStatus.Idle || connectionStatus is ConnectionStatus.Error) {
                val apiKey = preferencesManager.getApiKey()
                if (apiKey.isNotBlank()) {
                    // Trigger initialization. This should set the status to Loading internally.
                    GeminiConnectionManager.initialize(context, apiKey)
                } else {
                    // If API key is not set, immediately navigate to the API key screen.
                    navController.navigate("api_key_screen") {
                        popUpTo("loading_screen") { inclusive = true }
                    }
                }
            }
            // 상태가 이미 Loading 또는 Connected라면 이 LaunchedEffect는 추가적인 작업을 하지 않습니다.
        }

        // UI 및 네비게이션 based on connection status
        when (connectionStatus) {
            ConnectionStatus.Idle, ConnectionStatus.Loading -> {
                // 로딩 UI 표시 상태이거나, 로딩 상태일 때만 로딩 UI를 그립니다.
                // 이렇게 하면 빠른 연결 시 showLoadingUI가 true가 되지 않아 로딩 UI가 보이지 않습니다.
                if (showLoadingUI) { // showLoadingUI 상태가 true일 때만 로딩 인디케이터와 텍스트 표시
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Gemini와 연결 중...", style = MaterialTheme.typography.h6)
                } else {
                    // showLoadingUI가 false이면 (매우 빠른 연결이거나 아직 지연 시간 경과 전) 아무것도 표시하지 않거나 최소한의 UI 표시
                    // 여기서는 아무것도 표시하지 않아 빠른 전환 시 깜빡임을 줄입니다.
                }
            }
            ConnectionStatus.Connected -> {
                // 연결이 완료된 상태가 되면 채팅 목록 화면으로 이동합니다.
                // LaunchedEffect를 사용하여 상태 변화 시 네비게이션이 한 번만 일어나도록 합니다.
                LaunchedEffect(Unit) {
                    navController.navigate("chat_list_screen") {
                        // 로딩 화면을 백스택에서 제거하여 뒤로가기로 돌아오지 않도록 설정합니다.
                        popUpTo("loading_screen") { inclusive = true }
                    }
                }
                // 연결 완료 메시지를 잠깐 표시할 수 있습니다. (네비게이션이 즉시 일어나므로 보이지 않을 수 있습니다)
                // Text("연결 완료!", style = MaterialTheme.typography.h6) // 필요시 주석 해제
            }
            is ConnectionStatus.Error -> {
                // 연결 중 오류가 발생하면 에러 메시지를 표시합니다.
                Text(
                    text = "연결 실패... 네트워크 상태 혹은 API 사용 할당량을 확인해주세요",
                    color = Color.Red,
                    textAlign = TextAlign.Center // <-- 이 부분을 수정했습니다.
                )
                Spacer(modifier = Modifier.height(16.dp))
                // API 키 설정 화면으로 다시 이동하는 버튼
                Button(onClick = {
                    // API 키 설정 화면으로 이동하면서 로딩 화면을 백스택에서 제거
                    navController.navigate("api_key_screen") {
                        popUpTo("loading_screen") { inclusive = true }
                    }
                    // 오류 발생 후 Gemini 연결 상태를 Idle로 초기화하여 다음 시도를 가능하게 합니다.
                    GeminiConnectionManager.resetConnection()
                }) {
                    Text("API 키 다시 등록 / 설정")
                }
                // API 키 등록 화면으로 가지 않고 네트워크 확인 등 다른 안내를 원하시면 이 버튼을 수정하거나 다른 UI를 추가하세요.
            }
        }
    }
}