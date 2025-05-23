package com.squaredream.nemocharacterchat.ui.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.squaredream.nemocharacterchat.MainApplication
import com.squaredream.nemocharacterchat.R
import com.squaredream.nemocharacterchat.data.ChatRoom
import com.squaredream.nemocharacterchat.data.CharacterRepository
import com.squaredream.nemocharacterchat.data.PreferencesManager
import com.squaredream.nemocharacterchat.ui.theme.NavyBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatListScreen(navController: NavController) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val preferencesManager = PreferencesManager(context)

    // 자동 세션 초기화 코드 비활성화 (주석 처리)
    /*
    // 채팅방 목록 화면에 들어오면 모든 캐릭터의 세션을 미리 초기화
    LaunchedEffect(key1 = true) {
        // API 키가 설정되어 있는지 확인
        if (preferencesManager.isKeySet()) {
            val apiKey = preferencesManager.getApiKey()

            // 세션 매니저에 API 키가 설정되어 있지 않다면 설정
            if (apiKey.isNotEmpty()) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        // 세션 매니저 초기화
                        MainApplication.sessionManager.initialize(apiKey)

                        // 모든 캐릭터의 세션을 백그라운드에서 미리 생성
                        withContext(Dispatchers.IO) {
                            try {
                                MainApplication.sessionManager.getOrCreateSharedSession(apiKey, forceCreate = false)
                                Log.d("ChatListScreen", "Session preloaded")
                            } catch (e: Exception) {
                                Log.e("ChatListScreen", "Failed to preload session: ${e.message}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatListScreen", "Error initializing sessions: ${e.message}")
                    }
                }
            }
        }
    }
    */

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("채팅", color = Color.White) },
                backgroundColor = NavyBlue,
                contentColor = Color.White,
                elevation = 4.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
            )
        },
        backgroundColor = Color.White,
        modifier = Modifier.systemBarsPadding()
    ) { paddingValues ->
        // 캐릭터 데이터로부터 채팅방 목록 생성
        val characters = CharacterRepository.getAllCharacters()
        val chatRooms = characters.map { character ->
            ChatRoom(
                characterId = character.id,
                lastMessage = character.description,
                time = ""
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(chatRooms) { chatRoom ->
                ChatRoomItem(
                    chatRoom = chatRoom,
                    onClick = {
                        // 채팅방 ID를 통해 채팅 화면으로 이동
                        navController.navigate("chat_screen/${chatRoom.characterId}")
                    }
                )
            }
        }
    }
}

@Composable
fun ChatRoomItem(
    chatRoom: ChatRoom,
    onClick: () -> Unit
) {
    // 캐릭터 정보 가져오기
    val character = chatRoom.getCharacter()
    
    if (character != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 프로필 이미지
            Image(
                painter = painterResource(id = character.profileImage),
                contentDescription = "${character.name} 프로필",
                modifier = Modifier
                    .size(55.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            // 채팅방 정보 (이름, 마지막 메시지)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = character.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = chatRoom.lastMessage,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 시간 표시
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = chatRoom.time,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        // 구분선 추가
        Divider(
            modifier = Modifier.padding(start = 80.dp),
            color = Color.LightGray.copy(alpha = 0.5f)
        )
    }
}