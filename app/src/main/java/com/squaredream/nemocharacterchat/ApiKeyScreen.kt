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
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.squaredream.nemocharacterchat.MainApplication
import com.squaredream.nemocharacterchat.data.GeminiService
import com.squaredream.nemocharacterchat.data.PreferencesManager
import com.squaredream.nemocharacterchat.ui.theme.NemoCharacterChatTheme

@Composable
fun ApiKeyScreen(navController: NavController) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

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
            val annotatedText = buildAnnotatedString {
                append("직접 발급받은 Google Gemini API 키가 필요합니다.\n\nAPI 키는 ")
                // 링크 부분 스타일 지정 및 클릭 가능하게 설정
                pushStringAnnotation(tag = "URL", annotation = "https://aistudio.google.com/app/apikey")
                withStyle(
                    style = SpanStyle(
                        color = MaterialTheme.colors.primary, // 링크 색상
                        textDecoration = TextDecoration.Underline // 밑줄 추가
                    )
                ) {
                    append("aistudio.google.com/app/apikey")
                }
                pop()
                append(" 에서 \n무료로 발급받을 수 있습니다.")
            }

            ClickableText(
                text = annotatedText,
                style = MaterialTheme.typography.caption.copy(textAlign = TextAlign.Center), // 기존 스타일 적용 및 가운데 정렬
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                onClick = { offset ->
                    annotatedText.getStringAnnotations(tag = "URL", start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            uriHandler.openUri(annotation.item) // 링크 열기
                        }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

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
                text = "API 키에 할당된 한도를 초과할 시 예상치 못한 비용이 발생하거나\n채팅이 불가능할 수 있습니다.",
                style = MaterialTheme.typography.caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = TextAlign.Center
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

    // SessionManager에도 API 키 업데이트
    MainApplication.sessionManager.updateApiKey(apiKey)

    Toast.makeText(context, "API 키가 저장되었습니다", Toast.LENGTH_SHORT).show()
}

@Preview(showBackground = true)
@Composable
fun ApiKeyScreenPreview() {
    NemoCharacterChatTheme {
        ApiKeyScreen(navController = rememberNavController())
    }
}