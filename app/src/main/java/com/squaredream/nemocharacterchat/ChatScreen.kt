package com.squaredream.nemocharacterchat.ui.screens
import androidx.compose.foundation.Image
import com.squaredream.nemocharacterchat.data.GeminiChatService
import kotlinx.coroutines.delay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.squaredream.nemocharacterchat.R
import com.squaredream.nemocharacterchat.data.Character
import com.squaredream.nemocharacterchat.data.Message
import com.squaredream.nemocharacterchat.data.MessageType
import com.squaredream.nemocharacterchat.data.PreferencesManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 시간 포맷팅 함수 추가
fun getCurrentTime(): String {
    val formatter = SimpleDateFormat("a h:mm", Locale.KOREA)
    return formatter.format(Date())
}

@Composable
fun ChatScreen(navController: NavController, characterId: String) {
    // 키보드 상태 관찰
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val preferencesManager = remember { PreferencesManager(context) }
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 새 메시지 입력 상태
    var newMessageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    // 현재 선택된 캐릭터 정보 가져오기
    val character = when(characterId) {
        "raiden" -> Character(
            id = "raiden",
            name = "라이덴 쇼군",
            profileImage = R.drawable.raiden
        )
        "furina" -> Character(
            id = "furina",
            name = "푸리나",
            profileImage = R.drawable.furina
        )
        else -> Character(
            id = "unknown",
            name = "알 수 없음",
            profileImage = R.drawable.raiden
        )
    }

    // 채팅 메시지 상태 관리 - 빈 리스트로 시작
    val messages = remember { mutableStateListOf<Message>() }

    // 초기화 상태
    var isInitializing by remember { mutableStateOf(true) }

    // 캐릭터 초기화
    LaunchedEffect(characterId) {
        // 이미 메시지가 있으면 초기화 건너뛰기
        if (messages.isNotEmpty()) {
            isInitializing = false
            return@LaunchedEffect
        }

        isInitializing = true

        try {
            val apiKey = preferencesManager.getApiKey()

            // 캐릭터 초기화 및 첫 응답 가져오기
            val initialResponse = GeminiChatService.initializeCharacterChat(
                apiKey = apiKey,
                characterId = characterId
            )

            // 초기 메시지 추가
            val initialMessage = Message(
                id = "1",
                text = initialResponse,
                timestamp = getCurrentTime(), // 시간 포맷팅 함수 필요
                type = MessageType.RECEIVED,
                sender = character.name
            )

            messages.add(initialMessage)

        } catch (e: Exception) {
            // 오류 시 기본 메시지 추가
            val fallbackMessage = when(characterId) {
                "raiden" -> "여행자, 무슨 용건이지?"
                "furina" -> "어머, 드디어 내 팬이 찾아왔네요~! 파티를 준비했답니다~"
                else -> "안녕하세요."
            }

            messages.add(Message(
                id = "1",
                text = fallbackMessage,
                timestamp = getCurrentTime(),
                type = MessageType.RECEIVED,
                sender = character.name
            ))
        }

        isInitializing = false
    }

    // 새 메시지 전송 함수 (제미나이 AI전송)
    fun sendMessage() {
        if (newMessageText.isBlank()) return

        val userMessage = Message(
            id = (messages.size + 1).toString(),
            text = newMessageText,
            timestamp = getCurrentTime(),
            type = MessageType.SENT,
            sender = "나"
        )
        messages.add(userMessage)

        val textToSend = newMessageText // 상태 변경 전에 값 저장
        newMessageText = "" // 입력 필드 초기화 먼저 수행

        // 키보드 숨기기 및 포커스 제거
        keyboardController?.hide()
        focusManager.clearFocus()

        // 맨 아래로 스크롤 (사용자 메시지 추가 후)
        coroutineScope.launch {
            scrollState.animateScrollToItem(messages.size - 1)
        }

        // Gemini API 사용
        coroutineScope.launch {
            isLoading = true

            // API 호출 및 응답 생성
            val apiKey = preferencesManager.getApiKey()
            val responseText = try {
                GeminiChatService.generateResponse(
                    apiKey = apiKey,
                    userMessage = textToSend,
                    chatHistory = messages.dropLast(1),
                    characterId = characterId // 캐릭터 ID 전달
                )
            } catch (e: Exception) {
                "죄송합니다. 티바트에서 응답을 적어주지 않네요."
            }

            isLoading = false

            // 응답 메시지 추가
            val responseMessage = Message(
                id = (messages.size + 1).toString(),
                text = responseText,
                timestamp = getCurrentTime(),
                type = MessageType.RECEIVED,
                sender = character.name
            )
            messages.add(responseMessage)

            // 맨 아래로 스크롤 (응답 메시지 추가 후)
            scrollState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. 상단 앱바 - 고정 위치
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = character.profileImage),
                        contentDescription = "${character.name} 프로필",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = character.name)
                }
            },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로 가기")
                }
            },
            backgroundColor = Color.White,
            elevation = 1.dp
        )

        // 2. 채팅 콘텐츠 부분 - 키보드에 영향을 받을 부분
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // 메시지 목록
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 70.dp) // 하단 입력창 높이만큼 패딩
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageItem(message = message)
                }
            }

            // 하단 입력 영역
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            ) {
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .imePadding(), // 여기에만 imePadding 적용
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 메시지 입력 필드
                    TextField(
                        value = newMessageText,
                        onValueChange = { newMessageText = it },
                        placeholder = { Text("메시지 입력") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = Color.LightGray.copy(alpha = 0.2f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colors.primary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )

                    // 전송 버튼
                    IconButton(
                        onClick = { sendMessage() },
                        enabled = newMessageText.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (newMessageText.isNotBlank()) MaterialTheme.colors.primary else Color.Gray,
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = "전송",
                            tint = Color.White
                        )
                    }
                }
            }

            // 로딩 또는 초기화 중 표시
            if (isLoading || isInitializing) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // 초기 로딩 시 맨 아래로 스크롤
    LaunchedEffect(Unit) {
        if (messages.isNotEmpty()) {
            scrollState.scrollToItem(messages.size - 1)
        }
    }
}

@Composable
fun MessageItem(message: Message) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.type == MessageType.SENT) Alignment.End else Alignment.Start
    ) {
        // 메시지 시간 표시 (작은 텍스트)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.type == MessageType.SENT) Arrangement.End else Arrangement.Start
        ) {
            if (message.type == MessageType.RECEIVED) {
                Text(
                    text = message.sender,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }

        // 메시지 말풍선과 시간
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (message.type == MessageType.SENT) Arrangement.End else Arrangement.Start
        ) {
            // 내가 보낸 메시지일 경우 시간이 왼쪽에 위치
            if (message.type == MessageType.SENT) {
                Text(
                    text = message.timestamp,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                )
            }

            // 메시지 말풍선
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.type == MessageType.SENT) 16.dp else 0.dp,
                            bottomEnd = if (message.type == MessageType.SENT) 0.dp else 16.dp
                        )
                    )
                    .background(
                        if (message.type == MessageType.SENT) Color(0xFFFFE94A) // 노란색
                        else Color(0xFFFFFFFF) // 흰색
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    color = Color.Black
                )
            }

            // 상대방이 보낸 메시지일 경우 시간이 오른쪽에 위치
            if (message.type == MessageType.RECEIVED) {
                Text(
                    text = message.timestamp,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
        }
    }
}