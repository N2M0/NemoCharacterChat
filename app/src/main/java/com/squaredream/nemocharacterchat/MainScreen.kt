package com.squaredream.nemocharacterchat.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.unit.sp
import com.squaredream.nemocharacterchat.R
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.squaredream.nemocharacterchat.data.PreferencesManager
import com.squaredream.nemocharacterchat.ui.components.NavyIconButton
import com.squaredream.nemocharacterchat.ui.theme.NavyBlue
import com.squaredream.nemocharacterchat.ui.theme.NemoCharacterChatTheme

@Composable
fun MainScreen(navController: NavController) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 메인 콘텐츠
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 로고 이미지 중앙 정렬
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logoicon_transparent),
                    contentDescription = "Logo",
                    modifier = Modifier
                        .size(180.dp)
                        .padding(8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // 제목 텍스트 - 통일된 색상으로 변경
            Text(
                text = "원신\n캐릭터챗",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = NavyBlue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // 버튼 영역 - 중앙 정렬
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 채팅 버튼
                    NavyIconButton(
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
                        icon = Icons.Default.Group,
                        text = "채팅",
                        modifier = Modifier.weight(1f)
                    )
                    
                    Spacer(modifier = Modifier.width(24.dp))
                    
                    // API 키 등록 버튼
                    NavyIconButton(
                        onClick = { navController.navigate("api_key_screen") },
                        icon = Icons.Default.Settings,
                        text = "API 키 설정",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 주의사항 문구 - 하단에 고정
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "이 앱은 원신 팬메이드 앱으로, 수익을 창출하지 않습니다.\n\n앱을 사용하며 입력한 프롬프트와 결과물에 대한 책임은\n전적으로 사용자 본인에게 있습니다.\n\n코딩에 사용한 모델 : Claude 3.7 sonnet\n채팅용 모델 : Gemini 2.5 Flash\n제작자 : 네모난꿈",
                style = MaterialTheme.typography.caption,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    NemoCharacterChatTheme {
        MainScreen(navController = rememberNavController())
    }
}