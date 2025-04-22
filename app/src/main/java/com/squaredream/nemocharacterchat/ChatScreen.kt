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

@Composable
fun ChatScreen(navController: NavController, characterId: String) {
    // 키보드 상태 관찰
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }

    // 현재 선택된 캐릭터 정보 가져오기 (기존 로직 동일)
    val character = when(characterId) {
        "gemini" -> Character(
            id = "gemini",
            name = "Gemini",
            profileImage = R.drawable.gemini // Gemini 아이콘 리소스 필요
        )
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

    // 채팅 메시지 상태 관리 (기존 로직 동일)
    val messages = remember {
        when(characterId) {
            "gemini" -> mutableStateListOf(
                Message("1", "안녕하세요! 저는 Gemini입니다. 무엇을 도와드릴까요?", "방금 전", MessageType.RECEIVED, "Gemini")
            )
            "raiden" -> mutableStateListOf(
                Message("1", "여행자, 무슨 용건이지?", "오후 3:30", MessageType.RECEIVED, "라이덴 쇼군"),
                Message("2", "이나즈마에 오신 것을 환영해요.", "오후 3:31", MessageType.RECEIVED, "라이덴 쇼군"),
                Message("3", "안녕하세요, 쇼군님!", "오후 3:32", MessageType.SENT, "나")
            )
            "furina" -> mutableStateListOf(
                Message("1", "어머, 드디어 내 팬이 찾아왔네요~!", "오후 3:40", MessageType.RECEIVED, "푸리나"),
                Message("2", "어서 와요! 파티를 준비했답니다~", "오후 3:41", MessageType.RECEIVED, "푸리나"),
                Message("3", "안녕하세요, 푸리나님!", "오후 3:42", MessageType.SENT, "나")
            )
            else -> mutableStateListOf()
        }
    }

    // 새 메시지 입력 상태
    var newMessageText by remember { mutableStateOf("") }

    // 스크롤 상태 및 코루틴 스코프
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var isLoading by remember { mutableStateOf(false) }

    // 새 메시지 전송 함수 (제미나이 AI전송)
    fun sendMessage() {
        if (newMessageText.isBlank()) return

        val userMessage = Message(
            id = (messages.size + 1).toString(),
            text = newMessageText,
            timestamp = "지금", // 실제로는 시간 포맷팅 필요
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

        // Gemini API 사용 또는 자동 응답 처리
        coroutineScope.launch {
            isLoading = true

            // Gemini 캐릭터인 경우 API 사용
            val responseText = if (characterId == "gemini") {
                val apiKey = preferencesManager.getApiKey()
                try {
                    GeminiChatService.generateResponse(apiKey, textToSend, messages.dropLast(1))
                } catch (e: Exception) {
                    "죄송합니다. 응답을 생성하는 동안 오류가 발생했습니다."
                }
            } else {
                // 기존 캐릭터들은 하드코딩된 응답 사용
                delay(1000)
                when(characterId) {
                    "raiden" -> listOf("흥미롭군요.", "여행자, 이나즈마의 영원함을 느껴보세요.", "그것도 영원의 한 순간이 되겠군요.").random()
                    "furina" -> listOf("와아~ 정말 재밌네요!", "당신과 대화하는 건 언제나 즐거워요~", "다음 파티에도 꼭 초대할게요!").random()
                    else -> "..."
                }
            }

            isLoading = false

            val responseMessage = Message(
                id = (messages.size + 1).toString(),
                text = responseText,
                timestamp = "지금", // 실제로는 시간 포맷팅 필요
                type = MessageType.RECEIVED,
                sender = character.name
            )
            messages.add(responseMessage)

            // 맨 아래로 스크롤 (응답 메시지 추가 후)
            scrollState.animateScrollToItem(messages.size - 1)
        }
    }

    // --- 훨씬 간단한 구조로 수정 ---
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