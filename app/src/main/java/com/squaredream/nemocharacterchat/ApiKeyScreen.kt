package com.squaredream.nemocharacterchat.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.squaredream.nemocharacterchat.data.GeminiService
import com.squaredream.nemocharacterchat.data.PreferencesManager
import com.squaredream.nemocharacterchat.ui.theme.NemoCharacterChatTheme

@Composable
fun ApiKeyScreen(navController: NavController) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // API 키 상태 관리
    var apiKey by remember { mutableStateOf(preferencesManager.getApiKey()) }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API 키 등록") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "뒤로 가기"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // API 키 입력 필드
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API 키") },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "API 키 숨기기" else "API 키 보기"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // API 키 저장 버튼
            Button(
                onClick = {
                    if (apiKey.isBlank()) {
                        Toast.makeText(context, "API 키를 입력해주세요", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true
                    coroutineScope.launch {
                        val isValid = GeminiService.testApiKey(apiKey)
                        isLoading = false

                        if (isValid) {
                            saveApiKey(context, preferencesManager, apiKey)
                            navController.popBackStack()
                        } else {
                            Toast.makeText(context, "API 키가 유효하지 않습니다", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(0.7f),
                enabled = !isLoading
            ) {
                Text(if (isLoading) "검증 중..." else "API 키 저장")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 안내 문구
            Text(
                text = "Gemini API 키는 devices.google.com에서 발급받을 수 있습니다.",
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// API 키 저장 함수
private fun saveApiKey(
    context: Context,
    preferencesManager: PreferencesManager,
    apiKey: String
) {
    preferencesManager.saveApiKey(apiKey)
    preferencesManager.setKeyStatus(true)
    Toast.makeText(context, "API 키가 저장되었습니다", Toast.LENGTH_SHORT).show()
}

@Preview(showBackground = true)
@Composable
fun ApiKeyScreenPreview() {
    NemoCharacterChatTheme {
        ApiKeyScreen(navController = rememberNavController())
    }
}