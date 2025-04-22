package com.squaredream.nemocharacterchat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.squaredream.nemocharacterchat.R
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.squaredream.nemocharacterchat.data.PreferencesManager
import com.squaredream.nemocharacterchat.ui.theme.NemoCharacterChatTheme

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 제목 텍스트와 로고를 살짝 위로 배치
        Spacer(modifier = Modifier.weight(0.3f))

        // 원신 로고 이미지 추가
        Image(
            painter = painterResource(id = R.drawable.genshin_logo),
            contentDescription = "Genshin Impact Logo",
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            text = "원신 캐릭터챗",
            style = MaterialTheme.typography.h4,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 시작하기 버튼
        Button(
            onClick = {
                if (preferencesManager.isKeySet()) {
                    navController.navigate("chat_list_screen")
                } else {
                    Toast.makeText(
                        context,
                        "우선 Gemini API 키 등록이 필요합니다",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(vertical = 8.dp)
        ) {
            Text(text = "시작하기", style = MaterialTheme.typography.button)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // API 키 등록 버튼
        Button(
            onClick = { navController.navigate("api_key_screen") },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .padding(vertical = 8.dp)
        ) {
            Text(text = "API 키 등록", style = MaterialTheme.typography.button)
        }

        // 하단 여백
        Spacer(modifier = Modifier.weight(0.6f))

        // 주의사항 문구
        Text(
            text = "제작자 : 네모난꿈\n이 앱은 원신 팬메이드 앱이며, Gemini 기반으로 작동합니다. \n제작자는 사용자가 앱을 사용하며 입력한\n프롬프트와 결과물에 대해 책임지지 않습니다.",
            style = MaterialTheme.typography.caption,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    NemoCharacterChatTheme {
        MainScreen(navController = rememberNavController())
    }
}