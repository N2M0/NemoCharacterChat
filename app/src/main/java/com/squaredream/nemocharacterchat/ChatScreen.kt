package com.squaredream.nemocharacterchat.ui.screens

import androidx.compose.foundation.Image
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
import com.squaredream.nemocharacterchat.data.PreferencesManager // PreferencesManager는 필요시 사용 (API키 가져올 때)
import com.squaredream.nemocharacterchat.data.GeminiChatService // GeminiChatService 임포트 확인
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.compose.foundation.clickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 시간 포맷팅 함수 - 전역 유틸리티 함수로 선언
fun getCurrentTime(): String {
    val formatter = SimpleDateFormat("a h:mm", Locale.KOREA)
    return formatter.format(Date())
}

@Composable
fun ChatScreen(navController: NavController, characterId: String) { // Add navController parameter
    // ===== 컨텍스트 및 상태 관리 =====
    // 시스템 서비스
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // val preferencesManager = remember { PreferencesManager(context) } // API 키는 이제 ChatService 내부에서 필요시 가져옴
    val chatHistoryManager = remember { ChatHistoryManager(context) }

    // 스크롤 상태
    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 채팅 상태
    val messages = remember { mutableStateListOf<Message>() }
    var newMessageText by remember { mutableStateOf("") }

    // 로딩 상태
    var isLoading by remember { mutableStateOf(false) } // 메시지 전송 중 로딩
    var isScreenInitializing by remember { mutableStateOf(true) } // 화면 초기 로딩 (메시지 불러오기)

    // API 세션 준비 상태 (Gemini Chat 객체 생성 및 초기화/복원 완료 여부)
    var isGeminiSessionReady by remember { mutableStateOf(false) }

    // 로컬 저장소에서 메시지를 불러왔는지 여부 (첫 API 상호작용 시 복원 로직에 사용)
    var savedMessagesLoaded by remember { mutableStateOf(false) }

    // 하단 입력 필드 힌트 텍스트
    var placeholderText by remember { mutableStateOf("채팅 내역 불러오는 중...") }


    // 메뉴 및 대화상자 상태
    var menuExpanded by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }


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
            profileImage = R.drawable.raiden // 기본 이미지 설정
        )
    }

    // ===== 채팅 초기화 (새 채팅 시작) 함수 =====
    // 이 함수를 ChatScreen 컴포저블 본문 내, UI Column 바깥에 정의하여 다른 람다에서 접근 가능하게 합니다.
    fun resetChat() {
        coroutineScope.launch {
            try {
                // 로딩 상태로 설정
                isScreenInitializing = true // 초기화 과정 시작 (로딩 표시)
                isLoading = false // 메시지 전송 중 로딩은 초기화 시 false
                placeholderText = "세계수를 조작해 기록을 바꾸는 중..." // 초기화 중 힌트 텍스트

                // 메시지 목록 비우기
                messages.clear()
                newMessageText = "" // 입력 필드 초기화

                savedMessagesLoaded = false // 저장된 메시지 없음
                isGeminiSessionReady = false // Gemini 세션 준비 안됨

                // 로컬 채팅 기록 삭제
                chatHistoryManager.clearChatHistory(characterId)
                Log.d("ChatScreen", "Local chat history cleared for new chat.")

                // GeminiChatService 캐시에서 해당 캐릭터 세션 제거 (완전히 새로운 세션을 위해)
                GeminiChatService.clearCharacterChatCache(characterId)
                Log.d("ChatScreen", "Gemini session cache cleared for new chat.")

                // 새 채팅 시작 시 첫 인사말을 표시하지 않습니다.
                // 사용자가 첫 메시지를 보낼 때 prepareChatSessionAndGenerateResponse가 호출되어
                // 페르소나 설정 및 첫 응답이 이루어집니다.


                // 초기화 완료
                isScreenInitializing = false // 로딩 해제
                placeholderText = "메시지 입력" // 초기화 완료 후 힌트 텍스트

                // 스크롤 맨 위로 (빈 화면)
                coroutineScope.launch {
                    delay(50) // UI 업데이트 대기
                    scrollState.scrollToItem(0)
                }

            } catch (e: Exception) {
                Log.e("ChatScreen", "Error resetting chat: ${e.message}", e)
                messages.add(Message(
                    id = "system_reset_error_${System.currentTimeMillis()}", // 고유 ID 생성
                    text = "채팅을 초기화하는 중 오류가 발생했습니다",
                    timestamp = getCurrentTime(),
                    type = MessageType.RECEIVED,
                    sender = "티바트 시스템"
                ))
                isScreenInitializing = false
                isLoading = false
                isGeminiSessionReady = false
                placeholderText = "메시지 입력 (오류)"
            }
        }
    }


    // ===== 초기화 로직 (로컬 메시지 로딩) =====
    // characterId가 변경될 때마다 실행됩니다.
    LaunchedEffect(characterId) {
        // 화면 초기화 시작 (로컬 데이터 로딩)
        isScreenInitializing = true
        isLoading = false // API 로딩 상태 초기화
        placeholderText = "채팅 내역 불러오는 중..." // 로컬 로딩 중 힌트 텍스트

        messages.clear() // 이전 캐릭터의 메시지 목록 비우기

        try {
            // 1. 저장된 채팅 내역 불러오기
            val savedMessages = chatHistoryManager.loadChatHistory(characterId)

            if (savedMessages.isNotEmpty()) {
                // 저장된 메시지가 있으면 화면에 표시
                messages.addAll(savedMessages)
                savedMessagesLoaded = true // 저장된 메시지 로드 완료 상태 설정
                Log.d("ChatScreen", "Loaded ${savedMessages.size} saved messages for $characterId.")
            } else {
                // 저장된 메시지가 없으면 빈 상태로 시작
                savedMessagesLoaded = false
                Log.d("ChatScreen", "No saved messages found for $characterId.")
                // 저장된 메시지가 없는 경우에도 첫 메시지 전송 시 prepareChatSessionAndGenerateResponse가 호출됩니다.
                // 이때는 savedMessagesIfAny가 emptyList()로 전달되어 페르소나 설정만 진행됩니다.
            }

            // 화면 초기화 (로컬 로딩) 완료
            isScreenInitializing = false
            // Gemini 세션은 아직 준비되지 않았습니다. 첫 메시지 전송 시 prepareChatSessionAndGenerateResponse가 호출되어 준비됩니다.
            isGeminiSessionReady = false // 로컬 로딩 완료 후 세션 준비 상태 초기화
            placeholderText = "메시지 입력" // 로컬 로딩 완료 후 힌트 텍스트

            // 로드된 메시지 목록의 맨 아래로 스크롤
            coroutineScope.launch {
                delay(100) // UI 업데이트 대기
                if (messages.isNotEmpty()) {
                    scrollState.animateScrollToItem(messages.size - 1)
                }
            }

        } catch (e: Exception) {
            // 로컬 메시지 로딩 중 오류 발생 시 처리
            Log.e("ChatScreen", "Error during initial screen setup (loading messages): ${e.message}", e)
            messages.add(Message(
                id = "system_load_error_${System.currentTimeMillis()}", // 고유 ID 생성
                text = "이전 대화 내역을 불러오는 중 오류가 발생했습니다.",
                timestamp = getCurrentTime(),
                type = MessageType.RECEIVED,
                sender = "티바트 시스템"
            ))
            isScreenInitializing = false
            isLoading = false
            isGeminiSessionReady = false // 세션 준비 실패
            placeholderText = "메시지 입력 (오류)" // 오류 상태 힌트 텍스트
        }
    }


    // 채팅방을 나갈 때 채팅 내역 저장
    // characterId가 변경되거나 컴포저블이 dispose될 때 실행됩니다.
    DisposableEffect(characterId) {
        onDispose {
            // GlobalScope를 사용하여 코루틴 컨텍스트를 유지하고 파일 저장 작업을 수행합니다.
            GlobalScope.launch(Dispatchers.IO) { // IO Dispatcher 사용 권장 (파일 I/O)
                // 시스템 메시지 (sender="티바트 시스템")는 저장 대상에서 제외합니다.
                val messagesToSave = messages.filter { it.sender != "티바트 시스템" }

                if (messagesToSave.isNotEmpty()) {
                    chatHistoryManager.saveChatHistory(characterId, messagesToSave.toList())
                    Log.d("ChatScreen", "Chat history saved for character $characterId. Messages: ${messagesToSave.size}")
                } else {
                    // 메시지가 하나도 없거나 시스템 메시지만 있다면 해당 캐릭터의 기록을 삭제합니다.
                    chatHistoryManager.clearChatHistory(characterId)
                    Log.d("ChatScreen", "Chat history cleared for character $characterId as there were no non-system messages.")
                }

                // (선택 사항) 화면을 나갈 때 GeminiChatService 캐시에서 해당 세션을 제거할 수 있습니다.
                // 이렇게 하면 다음에 이 채팅방에 들어올 때 무조건 prepareChatSessionAndGenerateResponse를 다시 거치게 됩니다.
                // 메모리 관리에는 좋지만, 세션 재활용 효율은 떨어질 수 있습니다. 여기서는 제거하지 않는 것으로 합니다.
                // GeminiChatService.clearCharacterChatCache(characterId)
            }
        }
    }

    // 확인 대화상자
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("채팅 기록 초기화") },
            text = { Text("모든 채팅 내역을 지우고 새 채팅을 시작하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmationDialog = false
                        resetChat() // 채팅 초기화 함수 호출
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

    // ===== 메시지 전송 함수 ===== (스트리밍 방식 비동기 최적화)
    fun sendMessage() {
        // 메시지가 비어있거나 이미 API 로딩 중이거나 화면 초기화 중이면 무시
        if (newMessageText.isBlank() || isLoading || isScreenInitializing) return

        // 사용자 메시지 가져오고 입력 필드 비우기
        val userMessageText = newMessageText.trim() // 앞뒤 공백 제거
        if (userMessageText.isBlank()) return // 공백만 있는 메시지 무시

        newMessageText = "" // 입력 필드 비우기

        // UI에 즉시 사용자 메시지 추가
        val userMessage = Message(
            id = "user_${System.currentTimeMillis()}", // 고유 ID 생성
            text = userMessageText,
            timestamp = getCurrentTime(),
            type = MessageType.SENT,
            sender = "나"
        )
        messages.add(userMessage)


        // API 호출 로딩 상태 시작
        isLoading = true
        placeholderText = "상대의 답을 수신하고 있습니다..." // 로딩 중 힌트 텍스트

        // 키보드 숨기고 포커스 해제
        keyboardController?.hide()
        focusManager.clearFocus()

        // 사용자 메시지가 추가된 후 목록의 맨 아래로 스크롤
        coroutineScope.launch {
            delay(50) // UI 업데이트 대기
            if (messages.isNotEmpty()) {
                scrollState.animateScrollToItem(messages.size - 1)
            }
        }

        // AI 응답을 받을 빈 메시지 항목을 미리 추가합니다.
        // 스트리밍으로 오는 텍스트는 이 메시지에 누적됩니다.
        val aiResponseMessage = Message(
            id = "ai_${System.currentTimeMillis()}", // 고유 ID 생성
            text = "",  // 처음에는 빈 텍스트
            timestamp = getCurrentTime(),
            type = MessageType.RECEIVED,
            sender = character.name
        )
        messages.add(aiResponseMessage)

        // AI 응답 항목 추가 후 목록의 맨 아래로 다시 스크롤
        coroutineScope.launch {
            delay(50) // UI 업데이트 대기
            if (messages.isNotEmpty()) {
                scrollState.animateScrollToItem(messages.size - 1)
            }
        }


        // API 요청 및 응답 처리 (비동기 코루틴)
        coroutineScope.launch {
            try {
                val responseFlow = if (!isGeminiSessionReady) {
                    Log.d("ChatScreen", "First message send for this session. Preparing Gemini session (init/restore/persona) and generating response.")
                    // 해당 캐릭터와의 첫 API 상호작용: 세션 준비 및 응답 생성
                    // prepareChatSessionAndGenerateResponse 호출 시 세션 준비 완료 후 isGeminiSessionReady를 true로 설정합니다.
                    GeminiChatService.prepareChatSessionAndGenerateResponse(
                        context = context,
                        characterId = characterId,
                        userMessage = userMessageText, // 사용자가 보낸 현재 메시지
                        // 로컬에서 불러온 메시지 중 사용자가 보낸 메시지만 필터링하여 전달합니다.
                        savedMessagesIfAny = if (savedMessagesLoaded) messages.filter { it.sender != "티바트 시스템" && it.type == MessageType.SENT }.toList() else emptyList()
                    )
                } else {
                    Log.d("ChatScreen", "Subsequent message send. Generating response using existing session.")
                    // 이미 세션이 준비된 경우: 단순히 메시지만 전송하고 응답 생성
                    GeminiChatService.generateResponseStream(
                        userMessage = userMessageText,
                        characterId = characterId
                    )
                }

                // 응답 스트림 수집 및 UI 업데이트
                responseFlow.collect { streamResponse ->
                    if (streamResponse.error != null) {
                        Log.e("ChatScreen", "Streaming error: ${streamResponse.error}")
                        // 오류 발생 시, 미리 추가한 AI 메시지 항목을 제거하고 오류 메시지 항목을 추가합니다.
                        if (messages.lastOrNull()?.id == aiResponseMessage.id) { // 마지막 메시지가 AI 응답 항목인지 확인
                            messages.removeAt(messages.lastIndex)
                        }
                        messages.add(Message(
                            id = "system_api_error_${System.currentTimeMillis()}", // 고유 ID 생성
                            text = streamResponse.error,
                            timestamp = getCurrentTime(),
                            type = MessageType.RECEIVED,
                            sender = "티바트 시스템"
                        ))
                        // API 로딩 상태 해제 및 힌트 텍스트 복구
                        isLoading = false
                        placeholderText = "메시지 입력"
                        // 오류 발생 시 세션 상태를 '준비 안됨'으로 간주하여 다음 메시지 전송 시 재시도하도록 합니다.
                        isGeminiSessionReady = false
                        // 오류 발생 시점까지의 대화 내역 저장 (오류 메시지 포함 여부는 정책에 따라 결정)
                        chatHistoryManager.saveChatHistory(characterId, messages.filter { it.sender != "티바트 시스템" }.toList())
                        return@collect // 스트림 수집 중단
                    } else {
                        // 실시간으로 스트리밍되는 텍스트를 미리 추가한 AI 메시지 항목에 누적하여 업데이트합니다.
                        streamResponse.text?.let { text ->
                            // 메시지 리스트의 마지막 메시지 업데이트 (aiResponseMessage)
                            // 주의: concurrent modification을 피하기 위해 copy 후 교체
                            if (messages.lastOrNull()?.id == aiResponseMessage.id) { // 마지막 메시지가 AI 응답 항목인지 다시 확인
                                val updatedMessage = messages.last().copy(text = text)
                                messages[messages.lastIndex] = updatedMessage
                            } else {
                                // 예상치 못한 상황 (마지막 메시지가 AI 응답 항목이 아님) -> 로그 기록 또는 예외 처리
                                Log.w("ChatScreen", "Last message is not the expected AI response placeholder.")
                                // 이 경우, 새 AI 메시지로 추가하는 대신 기존 메시지 업데이트를 시도하지 않습니다.
                                // 실제 앱에서는 이럴 경우 새 메시지로 추가하는 로직을 고려할 수 있습니다.
                            }


                            // 새로운 텍스트가 추가될 때마다 스크롤을 맨 아래로 유지합니다.
                            coroutineScope.launch {
                                if (messages.isNotEmpty()) {
                                    scrollState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        }
                    }

                    // 응답 스트림 완료 처리
                    if (streamResponse.isComplete) {
                        isLoading = false // API 로딩 상태 해제
                        placeholderText = "메시지 입력" // 힌트 텍스트 복구
                        // 응답이 성공적으로 완료되면 Gemini 세션이 준비되었다고 표시합니다.
                        if (streamResponse.error == null) {
                            isGeminiSessionReady = true
                            Log.d("ChatScreen", "Streaming response completed successfully. Gemini session is ready.")
                        } else {
                            Log.w("ChatScreen", "Streaming response completed with error.")
                        }


                        // 응답 완료 시점에 채팅 내역을 저장합니다. (오류 시점에도 저장하도록 변경 가능)
                        chatHistoryManager.saveChatHistory(characterId, messages.filter { it.sender != "티바트 시스템" }.toList())
                    }
                }

            } catch (e: Exception) {
                // API 호출 자체 또는 스트림 수신 중 예외 발생 시 처리
                Log.e("ChatScreen", "Exception during API call or stream collection: ${e.message}", e)
                // 미리 추가한 AI 메시지 항목을 제거하고 오류 메시지 항목을 추가합니다.
                if (messages.lastOrNull()?.id == aiResponseMessage.id) { // 마지막 메시지가 AI 응답 항목인지 확인
                    messages.removeAt(messages.lastIndex)
                }
                messages.add(Message(
                    id = "system_exception_${System.currentTimeMillis()}", // 고유 ID 생성
                    text = "ERROR: 응답을 처리하는 동안 오류가 발생했습니다: ${e.message}",
                    timestamp = getCurrentTime(),
                    type = MessageType.RECEIVED,
                    sender = "티바트 시스템"
                ))
                // API 로딩 상태 해제 및 힌트 텍스트 복구
                isLoading = false
                placeholderText = "메시지 입력"
                // 예외 발생 시 세션 상태를 '준비 안됨'으로 간주하여 다음 메시지 전송 시 재시도하도록 합니다.
                isGeminiSessionReady = false
                // 예외 발생 시점까지의 대화 내역 저장 (오류 메시지 포함)
                chatHistoryManager.saveChatHistory(characterId, messages.filter { it.sender != "티바트 시스템" }.toList())
            }
        }
    }

    // ===== UI 구성 =====
    // Scaffold 대신 Column 사용
    Column(modifier = Modifier.fillMaxSize()) {
        // 상단 앱바
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
                        showConfirmationDialog = true // 초기화 확인 대화상자 표시
                    }) {
                        Text("세계수를 조작해 모든 대화내역을 없애고 새 채팅을 시작하기")
                    }
                }
            },
            // Material 3에서는 colors 사용 (프로젝트 테마에 따라 조정 필요)
            // backgroundColor = MaterialTheme.colors.primarySurface, // Material 2
            backgroundColor = MaterialTheme.colors.primaryVariant, // 예시로 Material 2의 primaryVariant 사용
            elevation = 1.dp // Material 2
        )

        // 메시지 목록 영역 (LazyColumn)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // 남은 세로 공간을 모두 차지
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp),
            state = scrollState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp) // 하단 입력창과의 간격은 Column의 padding으로 처리
        ) {
            items(messages, key = { it.id }) { message ->
                MessageItem(message = message)
            }
        }

        // 하단 입력 영역
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface) // 테마 색상 사용
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .imePadding(), // 키보드가 올라올 때 자동으로 패딩 조절
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 메시지 입력 필드
            TextField(
                value = newMessageText,
                onValueChange = { newMessageText = it },
                placeholder = { Text(placeholderText) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                colors = TextFieldDefaults.textFieldColors( // Material 2 TextField 색상 설정
                    backgroundColor = Color.LightGray.copy(alpha = 0.2f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colors.primary
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                enabled = !isScreenInitializing // 화면 초기화 중에는 입력 비활성화
            )

            // 전송 버튼/로딩 인디케이터
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape) // 클릭 영역도 CircleShape으로
                    .background(
                        color = if (newMessageText.isNotBlank() && !isLoading && !isScreenInitializing)
                            MaterialTheme.colors.primary // 활성화 상태 색상
                        else Color.Gray // 비활성화 상태 색상
                    )
                    .clickable( // Box 전체에 클릭 리스너 적용
                        enabled = newMessageText.isNotBlank() && !isLoading && !isScreenInitializing, // 활성화 조건
                        onClick = { sendMessage() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    // 메시지 전송 중 로딩 인디케이터
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    // 전송 버튼 아이콘
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "전송",
                        tint = Color.White // 아이콘 색상
                    )
                }
            }
        }
    }
}

// MessageItem Composable은 기존과 동일합니다.
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