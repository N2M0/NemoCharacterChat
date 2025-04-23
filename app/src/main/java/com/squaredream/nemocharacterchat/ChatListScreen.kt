package com.squaredream.nemocharacterchat.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.squaredream.nemocharacterchat.R
import com.squaredream.nemocharacterchat.data.ChatRoom

@Composable
fun ChatListScreen(navController: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("채팅") },
                backgroundColor = Color.White,
                elevation = 1.dp
            )
        }
    ) { paddingValues ->
        // 채팅방 목록 데이터 (추후 실제 데이터로 교체 가능)
        // ChatListScreen.kt - chatRooms 리스트에서 Gemini 항목 제거
        val chatRooms = listOf(
            ChatRoom(
                id = "raiden",
                name = "라이덴 쇼군",
                lastMessage = "이나즈마의 번개 신",
                time = "오후 10:49",
                profileImage = R.drawable.raiden
            ),
            ChatRoom(
                id = "furina",
                name = "푸리나",
                lastMessage = "폰타인의 물의 신?",
                time = "오후 10:40",
                profileImage = R.drawable.furina
            )
        )

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
                        navController.navigate("chat_screen/${chatRoom.id}")
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 프로필 이미지
        Image(
            painter = painterResource(id = chatRoom.profileImage),
            contentDescription = "${chatRoom.name} 프로필",
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
                text = chatRoom.name,
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