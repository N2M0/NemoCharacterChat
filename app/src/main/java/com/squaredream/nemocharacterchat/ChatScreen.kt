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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.squaredream.nemocharacterchat.R
import com.squaredream.nemocharacterchat.data.Character
import com.squaredream.nemocharacterchat.data.ChatHistoryManager
import com.squaredream.nemocharacterchat.data.Message
import com.squaredream.nemocharacterchat.data.MessageType
import com.squaredream.nemocharacterchat.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 시간 포맷팅 함수 - 전역 유틸리티 함수로 선언
fun getCurrentTime(): String {
    val formatter = SimpleDateFormat("a h:mm", Locale.KOREA)
    return formatter.format(Date())
}

@Composable
fun ChatScreen(navController: NavController, characterId: String) {
    // ===== 컨텍스트 및 상태 관리 =====
    // 시스템 서비스
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val preferencesManager = remember { PreferencesManager(context) }
    val chatHistoryManager = remember { ChatHistoryManager(context) }

    // 스크롤 상태
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 채팅 상태
    val messages = remember { mutableStateListOf<Message>() }
    var internalChatHistory by remember { mutableStateOf<List<Message>>(emptyList()) }
    var newMessageText by remember { mutableStateOf("") }

    // 로딩 상태
    var isLoading by remember { mutableStateOf(false) }
    var isInitializing by remember { mutableStateOf(true) }
    var placeholderText by remember { mutableStateOf("티바트에 연결 중입니다...") }

    // 메뉴 및 대화상자 상태
    var menuExpanded by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    // 세션 복원 상태 - 저장된 메시지가 있을 때 첫 메시지 전송 시 세션 복원 필요
    var needsSessionRestoration by remember { mutableStateOf(false) }
    var savedMessagesLoaded by remember { mutableStateOf(false) }

    // ===== 캐릭터 정보 =====
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

    // ===== 채팅 초기화 함수 =====
    fun resetChat() {
        coroutineScope.launch {
            try {
                // 로딩 상태로 설정
                isInitializing = true
                placeholderText = "세계수를 조작해 기록을 바꾸는 중..."

                // 메시지 목록 비우기
                messages.clear()
                internalChatHistory = emptyList()

                // 세션 초기화
                GeminiChatService.clearCharacterChat(characterId)
                chatHistoryManager.clearChatHistory(characterId)

                // Gemini API로 새 대화 시작
                val apiKey = preferencesManager.getApiKey()
                val initialResponse = GeminiChatService.performInitialExchange(apiKey, characterId)

                if (initialResponse == "ERROR") {
                    // 오류 발생 시 시스템 메시지 표시
                    messages.add(Message(
                        id = "1",
                        text = "지맥 오류가 생겼습니다. 초기화를 다시 시도해주세요.",
                        timestamp = getCurrentTime(),
                        type = MessageType.RECEIVED,
                        sender = "티바트 시스템"
                    ))
                } else {
                    // 캐릭터의 첫 인사말을 화면에 직접 표시
                    messages.add(Message(
                        id = "1",
                        text = initialResponse,
                        timestamp = getCurrentTime(),
                        type = MessageType.RECEIVED,
                        sender = character.name
                    ))
                }

                // 초기화 완료
                isInitializing = false
                placeholderText = "메시지 입력"

                // 새 메시지로 스크롤
                scrollState.animateScrollToItem(0)

            } catch (e: Exception) {
                Log.e("ChatScreen", "Error resetting chat: ${e.message}", e)
                messages.add(Message(
                    id = "1",
                    text = "지맥 오류가 생겼습니다. 초기화를 다시 시도해주세요.",
                    timestamp = getCurrentTime(),
                    type = MessageType.RECEIVED,
                    sender = "티바트 시스템"
                ))
                isInitializing = false
                placeholderText = "메시지 입력"
            }
        }
    }

    // ===== 초기화 로직 =====
    LaunchedEffect(characterId) {
        // 이미 메시지가 있으면 초기화 건너뛰기
        if (messages.isNotEmpty()) {
            isInitializing = false
            placeholderText = "메시지 입력"
            return@LaunchedEffect
        }

        isInitializing = true
        placeholderText = "티바트에 연결 중입니다..."

        try {
            // 저장된 채팅 내역 불러오기
            val savedMessages = chatHistoryManager.loadChatHistory(characterId)

            if (savedMessages.isNotEmpty()) {
                // 저장된 메시지가 있으면 표시
                messages.addAll(savedMessages)
                // 내부 채팅 기록에도 추가 (시스템 메시지 제외)
                internalChatHistory = savedMessages.filter { it.sender != "티바트 시스템" }

                // 세션 복원 필요 상태 설정
                needsSessionRestoration = true
                savedMessagesLoaded = true

                isInitializing = false
                placeholderText = "메시지 입력"

                // 스크롤 맨 아래로
                coroutineScope.launch {
                    delay(100) // UI 업데이트 대기
                    scrollState.animateScrollToItem(messages.size - 1)
                }

                return@LaunchedEffect
            }

            // 저장된 메시지가 없으면 API 초기화 진행
            val apiKey = preferencesManager.getApiKey()

            // 초기 응답 가져오기 (CHARACTER_PROMPTS만 보내고 응답 받기)
            val initialResponse = GeminiChatService.performInitialExchange(apiKey, characterId)

            if (initialResponse == "ERROR") {
                // 오류 발생 시 시스템 메시지 표시
                messages.add(Message(
                    id = "1",
                    text = "지맥 오류 발생! 우측 상단 메뉴의 초기화 기능을 사용해주세요.",
                    timestamp = getCurrentTime(),
                    type = MessageType.RECEIVED,
                    sender = "티바트 시스템"
                ))
            } else {
                // 캐릭터의 첫 인사말을 화면에 직접 표시
                messages.add(Message(
                    id = "1",
                    text = initialResponse,
                    timestamp = getCurrentTime(),
                    type = MessageType.RECEIVED,
                    sender = character.name
                ))

                // 내부 대화 기록은 빈 상태로 시작 (사용자의 첫 메시지가 없으므로)
                internalChatHistory = emptyList()
            }

            // 초기화 완료
            isInitializing = false
            placeholderText = "메시지 입력"

        } catch (e: Exception) {
            // 예외 발생 시 시스템 메시지 표시
            messages.add(Message(
                id = "1",
                text = "지맥 오류 발생! 우측 상단 메뉴의 초기화 기능을 사용해주세요.",
                timestamp = getCurrentTime(),
                type = MessageType.RECEIVED,
                sender = "티바트 시스템"
            ))

            isInitializing = false
            placeholderText = "메시지 입력"
        }
    }

    // 채팅방을 나갈 때 채팅 내역 저장
    DisposableEffect(characterId) {
        onDispose {
            if (messages.isNotEmpty()) {
                // 코루틴 컨텍스트 밖에서 호출되므로 GlobalScope 사용
                GlobalScope.launch {
                    chatHistoryManager.saveChatHistory(characterId, messages)
                }
            }
        }
    }

    // 확인 대화상자
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("세계수를 조작해 채팅 기록 초기화") },
            text = { Text("모든 채팅 기록을 지우고\n새 채팅을 시작하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmationDialog = false
                        resetChat()
                    }
                ) {
                    Text("예")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmationDialog = false }
                ) {
                    Text("아니오")
                }
            }
        )
    }

    // ===== 메시지 전송 함수 ===== (스트리밍 버전)
    fun sendMessage() {
        // 메시지가 비어있거나 이미 로딩 중이면 무시
        if (newMessageText.isBlank() || isLoading || isInitializing) return

        // 사용자 메시지 추가
        val userMessage = Message(
            id = (messages.size + 1).toString(),
            text = newMessageText,
            timestamp = getCurrentTime(),
            type = MessageType.SENT,
            sender = "나"
        )
        messages.add(userMessage)

        val textToSend = newMessageText
        newMessageText = ""

        // 로딩 상태 업데이트
        isLoading = true
        placeholderText = "상대의 답을 수신하고 있습니다..."

        // 키보드 제어 및 포커스 해제
        keyboardController?.hide()
        focusManager.clearFocus()

        // 스크롤 처리
        coroutineScope.launch {
            scrollState.animateScrollToItem(messages.size - 1)
        }

        // AI 응답 메시지를 미리 추가 (스트리밍 업데이트를 위한 빈 메시지)
        val aiResponseMessage = Message(
            id = (messages.size + 1).toString(),
            text = "",  // 처음에는 빈 텍스트
            timestamp = getCurrentTime(),
            type = MessageType.RECEIVED,
            sender = character.name
        )
        messages.add(aiResponseMessage)

        // API 요청 및 응답 처리 (스트리밍)
        coroutineScope.launch {
            val apiKey = preferencesManager.getApiKey()

            // 세션 복원이 필요한 경우 (저장된 메시지가 있고 첫 메시지를 보내는 경우)
            if (needsSessionRestoration && savedMessagesLoaded) {
                Log.d("ChatScreen", "First message after restore, restoring session...")

                // 이전 대화 내역을 모두 전송하여 컨텍스트 복원
                val success = GeminiChatService.restoreSession(
                    apiKey = apiKey,
                    characterId = characterId,
                    savedMessages = internalChatHistory
                )

                if (!success) {
                    // 세션 복원 실패 시 사용자에게 알림
                    messages.removeAt(messages.lastIndex) // 미리 추가한 AI 메시지 제거
                    messages.add(Message(
                        id = (messages.size + 1).toString(),
                        text = "이전 대화 내역을 복원하는 중 문제가 발생했습니다.",
                        timestamp = getCurrentTime(),
                        type = MessageType.RECEIVED,
                        sender = "티바트 시스템"
                    ))
                    isLoading = false
                    placeholderText = "메시지 입력"
                    return@launch
                }

                // 세션 복원 완료, 더 이상 복원 필요 없음
                needsSessionRestoration = false
                savedMessagesLoaded = false
            }

            // 스트리밍 API 호출 및 실시간 업데이트
            try {
                // 스트리밍 응답 시작
                var finalResponse = ""
                var hasError = false

                GeminiChatService.generateResponseStream(
                    apiKey = apiKey,
                    userMessage = textToSend,
                    chatHistory = internalChatHistory,
                    characterId = characterId
                ).collect { streamResponse ->
                    if (streamResponse.error != null) {
                        // 오류 메시지 처리
                        hasError = true
                        messages.removeAt(messages.lastIndex) // 미리 추가한 AI 메시지 제거
                        messages.add(Message(
                            id = (messages.size + 1).toString(),
                            text = streamResponse.error,
                            timestamp = getCurrentTime(),
                            type = MessageType.RECEIVED,
                            sender = "티바트 시스템"
                        ))
                    } else {
                        // 실시간으로 메시지 업데이트
                        streamResponse.text?.let { text ->
                            // 메시지 리스트의 마지막 메시지 업데이트 (aiResponseMessage)
                            val updatedMessage = messages.last().copy(text = text)
                            messages[messages.lastIndex] = updatedMessage
                            finalResponse = text

                            // 스크롤 유지
                            coroutineScope.launch {
                                scrollState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    }

                    // 응답 완료 처리
                    if (streamResponse.isComplete) {
                        isLoading = false
                        placeholderText = "메시지 입력"
                    }
                }

                // 정상 응답인 경우에만 내부 채팅 기록 업데이트
                if (!hasError) {
                    val finalAiMessage = messages.last()

                    // 내부 채팅 기록 업데이트 - 최신 교환 내용 추가
                    internalChatHistory = internalChatHistory + listOf(
                        userMessage,      // 방금 보낸 사용자 메시지
                        finalAiMessage    // 최종 완성된 AI 응답
                    )

                    // 채팅 기록이 너무 길어지면 가장 오래된 메시지부터 제거
                    if (internalChatHistory.size > 20) {
                        internalChatHistory = internalChatHistory.drop(internalChatHistory.size - 20)
                    }
                }

            } catch (e: Exception) {
                // 예외 발생 시 처리
                Log.e("ChatScreen", "Error in streaming response: ${e.message}", e)
                messages.removeAt(messages.lastIndex) // 미리 추가한 AI 메시지 제거
                messages.add(Message(
                    id = (messages.size + 1).toString(),
                    text = "ERROR: 응답을 생성하는 동안 오류가 발생했습니다.",
                    timestamp = getCurrentTime(),
                    type = MessageType.RECEIVED,
                    sender = "티바트 시스템"
                ))
                isLoading = false
                placeholderText = "메시지 입력"
            }

            // 채팅 내역 저장 (비동기적으로 수행)
            chatHistoryManager.saveChatHistory(characterId, messages)
        }
    }

    // ===== UI 구성 =====
    Column(modifier = Modifier.fillMaxSize()) {
        // 상단 앱바 (메뉴 추가)
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
            actions = {
                // 메뉴 아이콘 추가
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "메뉴")
                }

                // 드롭다운 메뉴
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(onClick = {
                        menuExpanded = false
                        showConfirmationDialog = true
                    }) {
                        Text("채팅 초기화")
                    }
                }
            },
            backgroundColor = Color.White,
            elevation = 1.dp
        )

        // 채팅 영역
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
                contentPadding = PaddingValues(top = 8.dp, bottom = 70.dp)
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
                Divider(color = Color.Transparent)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 메시지 입력 필드 - 초기화 중에도 입력 가능하게 수정
                    TextField(
                        value = newMessageText,
                        onValueChange = { newMessageText = it }, // 항상 입력 허용
                        placeholder = { Text(placeholderText) },
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
                        enabled = true // 항상 활성화
                    )

                    // 전송 버튼/로딩 인디케이터
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (newMessageText.isNotBlank() && !isLoading && !isInitializing)
                                    MaterialTheme.colors.primary
                                else Color.Gray,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading || isInitializing) {
                            // 로딩 중 인디케이터
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            // 전송 버튼
                            IconButton(
                                onClick = { sendMessage() },
                                enabled = newMessageText.isNotBlank(),
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

    // 일반 메시지 처리
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.type == MessageType.SENT) Alignment.End else Alignment.Start
    ) {
        // 발신자 이름 (받은 메시지의 경우)
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
            // 시간 (보낸 메시지)
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

            // 시간 (받은 메시지)
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