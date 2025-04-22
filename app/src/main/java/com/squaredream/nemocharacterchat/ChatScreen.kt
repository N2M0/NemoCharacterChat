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
import androidx.compose.ui.text.style.TextAlign
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
    var internalChatHistory by remember { mutableStateOf<List<Message>>(emptyList()) }

    // 새 메시지 입력 상태
    var newMessageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(true) }
    var placeholderText by remember { mutableStateOf("티바트에 연결 중입니다...") }

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

    // 캐릭터 초기화
    LaunchedEffect(characterId) {
        if (messages.isNotEmpty()) {
            isInitializing = false
            placeholderText = "메시지 입력"
            return@LaunchedEffect
        }

        isInitializing = true
        placeholderText = "티바트에 연결 중입니다..."

        try {
            val apiKey = preferencesManager.getApiKey()

            // 내부 메시지 리스트 (UI에 표시되지 않음)
            val internalMessages = mutableListOf<Message>()

            // 첫 번째 교환 수행 (UI에 표시하지 않음)
            val (userMessage, aiResponse) = GeminiChatService.performInitialExchange(apiKey, characterId)

            // 내부 메시지 목록에 추가 (UI에는 표시하지 않음)
            internalMessages.add(Message(
                id = "internal_1",
                text = userMessage,
                timestamp = getCurrentTime(),
                type = MessageType.SENT,
                sender = "나"
            ))

            internalMessages.add(Message(
                id = "internal_2",
                text = aiResponse,
                timestamp = getCurrentTime(),
                type = MessageType.RECEIVED,
                sender = character.name
            ))

            // UI에 표시할 시스템 메시지
            messages.add(Message(
                id = "1",
                text = "티바트에 오신 것을 환영합니다.",
                timestamp = getCurrentTime(),
                type = MessageType.RECEIVED,
                sender = "티바트 시스템"
            ))

            // 내부 메시지를 별도로 저장
            internalChatHistory = internalMessages.toList()

            // 초기화 완료
            isInitializing = false
            placeholderText = "메시지 입력"

        } catch (e: Exception) {
            // 예외 발생 시 시스템 메시지만 표시
            messages.add(Message(
                id = "1",
                text = "연결 중 오류가 발생했습니다. 다시 시도해주세요.",
                timestamp = getCurrentTime(),
                type = MessageType.RECEIVED,
                sender = "티바트 시스템"
            ))

            isInitializing = false
            placeholderText = "메시지 입력"
        }
    }

    // 새 메시지 전송 함수 (제미나이 AI전송)
    fun sendMessage() {
        if (newMessageText.isBlank() || isLoading) return

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

        //로딩으로 상태 변경
        isLoading = true
        placeholderText = "상대의 답을 수신하고 있습니다..."

        // 키보드 숨기기 및 포커스 제거
        keyboardController?.hide()
        focusManager.clearFocus()

        // 맨 아래로 스크롤 (사용자 메시지 추가 후)
        coroutineScope.launch {
            scrollState.animateScrollToItem(messages.size - 1)
        }

        // Gemini API 호출
        coroutineScope.launch {
            // 내부 채팅 기록과 표시된 메시지를 합쳐서 전송
            val fullChatHistory = internalChatHistory + messages.filter { it.sender != "티바트 시스템" }

            // API 호출 및 응답 생성
            val apiKey = preferencesManager.getApiKey()
            val responseText = try {
                GeminiChatService.generateResponse(
                    apiKey = apiKey,
                    userMessage = textToSend,
                    chatHistory = fullChatHistory,
                    characterId = characterId
                )
            } catch (e: Exception) {
                "ERROR: 응답을 생성하는 동안 오류가 발생했습니다."
            }

            // 로딩 완료 상태로 변경
            isLoading = false
            placeholderText = "메시지 입력"

            // 응답이 오류인지 확인하고 적절한 메시지 추가
            if (responseText.startsWith("ERROR:")) {
                // 오류 메시지를 시스템 메시지로 표시
                val errorMessage = responseText.substringAfter("ERROR: ")
                messages.add(Message(
                    id = (messages.size + 1).toString(),
                    text = errorMessage,
                    timestamp = getCurrentTime(),
                    type = MessageType.RECEIVED,
                    sender = "티바트 시스템"
                ))
            } else {
                // 정상 응답은 캐릭터 메시지로 표시
                messages.add(Message(
                    id = (messages.size + 1).toString(),
                    text = responseText,
                    timestamp = getCurrentTime(),
                    type = MessageType.RECEIVED,
                    sender = character.name
                ))
            }

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
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 메시지 입력 필드 - 플레이스홀더 텍스트 변경
                    TextField(
                        value = newMessageText,
                        onValueChange = { newMessageText = it },
                        placeholder = { Text(placeholderText) }, // 동적 플레이스홀더
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
                        singleLine = true,
                        enabled = !isInitializing // 초기화 중에는 비활성화
                    )

                    // 전송 버튼 - 로딩 인디케이터 추가
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (newMessageText.isNotBlank() && !isInitializing)
                                    MaterialTheme.colors.primary
                                else Color.Gray,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            // 로딩 중일 때 CircularProgressIndicator 표시
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            // 로딩 중이 아닐 때 버튼 표시
                            IconButton(
                                onClick = { sendMessage() },
                                enabled = newMessageText.isNotBlank() && !isInitializing,
                                modifier = Modifier.matchParentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Send,
                                    contentDescription = "전송",
                                    tint = Color.White
                                )
                            }
                        }
                    }
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
    // 시스템 메시지 처리
    if (message.sender == "티바트 시스템") {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message.text,
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .background(
                        color = Color.LightGray.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        return
    }

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