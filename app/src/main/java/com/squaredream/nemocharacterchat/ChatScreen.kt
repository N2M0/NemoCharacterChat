package com.squaredream.nemocharacterchat.ui.screens
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.squaredream.nemocharacterchat.ui.theme.NavyBlue
import com.squaredream.nemocharacterchat.ui.theme.IconTextColor

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

                // 진행 상태 메시지 목록
                val resetMessages = listOf(
                    "입력 중...",
                    "지맥 상태 분석 중...",
                    "지맥 네트워크에 침투 중...",
                    "지맥에서 데이터 추출 중...",
                    "추출한 데이터를 파악하는 중...",
                    "파악한 데이터를 출력하는 중...",
                    "오류 확인 중..."
                )

                // 초기 상태 메시지 추가
                messages.add(Message(
                    id = "1",
                    text = resetMessages[0],
                    timestamp = getCurrentTime(),
                    type = MessageType.RECEIVED,
                    sender = character.name
                ))

                // 진행 상태 업데이트 Job
                var progressJob: Job? = null

                // 진행 상태 업데이트 작업 시작
                progressJob = launch {
                    var index = 0
                    while (isActive) {
                        delay(6500) // 6.5초마다 메시지 변경
                        if (index < resetMessages.size - 1) {
                            index++
                            // 메시지 업데이트
                            val updatedMessage = messages.last().copy(text = resetMessages[index])
                            messages[0] = updatedMessage  // 첫 번째 메시지 업데이트
                        } else {
                            // 마지막 메시지("오류 확인 중...") 도달 시 더 이상 변경하지 않음
                            break
                        }
                    }
                }

                // 세션 초기화
                GeminiChatService.clearCharacterChat(characterId)
                chatHistoryManager.clearChatHistory(characterId)

                // Gemini API로 새 대화 시작
                val apiKey = preferencesManager.getApiKey()
                val initialResponse = GeminiChatService.performInitialExchange(apiKey, characterId)

                // 진행 상태 업데이트 중지
                progressJob?.cancel()

                if (initialResponse == "ERROR") {
                    // 오류 발생 시 시스템 메시지 표시
                    messages.clear()  // 진행 상태 메시지 제거
                    messages.add(Message(
                        id = "1",
                        text = "지맥 오류가 생겼습니다. 초기화를 다시 시도해주세요.",
                        timestamp = getCurrentTime(),
                        type = MessageType.RECEIVED,
                        sender = "티바트 시스템"
                    ))
                } else {
                    // 캐릭터의 첫 인사말을 화면에 직접 표시
                    messages.clear()  // 진행 상태 메시지 제거
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
                messages.clear()  // 진행 상태 메시지 제거
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

        // 진행 상태 메시지 목록
        val initializingMessages = listOf(
            "입력 중...",
            "지맥 상태 분석 중...",
            "지맥 네트워크에 침투 중...",
            "지맥에서 데이터 추출 중...",
            "추출한 데이터를 파악하는 중...",
            "파악한 데이터를 출력하는 중...",
            "오류 확인 중..."
        )

        // 초기화 상태 메시지를 추가 (추후 업데이트)
        val initMessage = Message(
            id = "1",
            text = initializingMessages[0],
            timestamp = getCurrentTime(),
            type = MessageType.RECEIVED,
            sender = character.name
        )
        messages.add(initMessage)

        // 진행 상태 업데이트 Job
        var progressJob: Job? = null

        try {
            // 진행 상태 업데이트 작업 시작
            progressJob = launch {
                var index = 0
                while (isActive) {
                    delay(6500) // 6.5초마다 메시지 변경
                    if (index < initializingMessages.size - 1) {
                        index++
                        // 메시지 업데이트
                        val updatedMessage =
                            messages.last().copy(text = initializingMessages[index])
                        messages[0] = updatedMessage  // 첫 번째 메시지 업데이트
                    } else {
                        // 마지막 메시지("오류 확인 중...") 도달 시 더 이상 변경하지 않음
                        break
                    }
                }
            }

            // API 키 가져오기
            val apiKey = preferencesManager.getApiKey()

            // STEP 1: 저장된 채팅 내역 불러오기
            val savedMessages = chatHistoryManager.loadChatHistory(characterId)

            // STEP 2: 세션 존재 여부 확인
            val sessionExists = coroutineScope.async(Dispatchers.IO) {
                GeminiChatService.checkSessionExists(apiKey, characterId)
            }.await()

            Log.d("ChatScreen", "Session exists check: $sessionExists")

            if (savedMessages.isNotEmpty()) {
                // 저장된 메시지가 있으면 표시
                progressJob?.cancel()  // 진행 상태 업데이트 중지
                messages.clear()  // 진행 상태 메시지 제거
                messages.addAll(savedMessages)

                // 내부 채팅 기록에도 추가 (시스템 메시지 제외)
                internalChatHistory = savedMessages.filter { it.sender != "티바트 시스템" }

                // 세션 복원 필요 여부 설정 - 세션이 없는 경우에만 복원 필요
                needsSessionRestoration = !sessionExists
                savedMessagesLoaded = true

                isInitializing = false
                placeholderText = "메시지 입력"

                // 스크롤 맨 아래로
                delay(100) // UI 업데이트 대기
                scrollState.animateScrollToItem(messages.size - 1)

                return@LaunchedEffect
            }

            // STEP 3: 저장된 메시지가 없는 경우 - 새 채팅 시작
            // 세션이 이미 존재하면 재활용, 없으면 새로 생성

            // 첫 인사말 메시지 가져오기 (세션 재활용 여부와 관계없이 Gemini에서 받아옴)
            val initialResponse = if (sessionExists) {
                // 기존 세션이 있으면 getWelcomeMessage 사용 (세션 초기화 없이 인사말만 받아옴)
                GeminiChatService.getWelcomeMessage(apiKey, characterId)
            } else {
                // 세션이 없으면 performInitialExchange 사용 (첫 응답 + 백그라운드 세션 초기화)
                GeminiChatService.performInitialExchange(apiKey, characterId)
            }

            // 진행 상태 업데이트 중지
            progressJob?.cancel()

            if (initialResponse == "ERROR") {
                // 오류 발생 시 티바트 시스템 메시지 표시
                messages.clear()  // 진행 상태 메시지 제거
                messages.add(
                    Message(
                        id = "1",
                        text = "지맥 오류가 발생했습니다. 네트워크 상태 확인 후 우측 상단 채팅 초기화 기능을 사용해주세요.",
                        timestamp = getCurrentTime(),
                        type = MessageType.RECEIVED,
                        sender = "티바트 시스템"
                    )
                )
            } else {
                // 캐릭터의 첫 인사말을 화면에 직접 표시
                messages.clear()  // 진행 상태 메시지 제거
                messages.add(
                    Message(
                        id = "1",
                        text = initialResponse,
                        timestamp = getCurrentTime(),
                        type = MessageType.RECEIVED,
                        sender = character.name
                    )
                )

                // 내부 대화 기록은 빈 상태로 시작 (사용자의 첫 메시지가 없으므로)
                internalChatHistory = emptyList()
            }

            // 초기화 완료
            isInitializing = false
            placeholderText = "메시지 입력"

        } catch (e: Exception) {
            // 진행 상태 업데이트 중지
            progressJob?.cancel()

            // 예외 발생 시 상세한 오류 로깅
            Log.e("ChatScreen", "Error initializing chat: ${e.message}", e)

            // 사용자에게 보여줄 오류 메시지 설정
            val errorMessage = when {
                e.message?.contains("network", ignoreCase = true) == true ->
                    "지맥 오류가 발생했습니다. 네트워크 상태 확인 후 우측 상단 채팅 초기화 기능을 사용해주세요."
                //"네트워크 연결 오류가 발생했습니다. 인터넷 연결을 확인하고 다시 시도해주세요."
                e.message?.contains("timeout", ignoreCase = true) == true ->
                    "지맥 오류가 발생했습니다. 서버 상태 확인 후 우측 상단 채팅 초기화 기능을 사용해주세요."
                //"서버 응답 시간이 초과되었습니다. 잠시 후 다시 시도해주세요."
                e.message?.contains("api", ignoreCase = true) == true ->
                    "API 키 관련 오류가 발생했습니다. API 키 설정 혹은 남은 사용량을 확인해주세요."

                else ->
                    "지맥 오류가 발생했습니다. 우측 상단 메뉴의 채팅 초기화 기능을 사용해주세요."
            }

            // 진행 상태 메시지 제거하고 오류 메시지 표시
            messages.clear()
            messages.add(
                Message(
                    id = "1",
                    text = errorMessage,
                    timestamp = getCurrentTime(),
                    type = MessageType.RECEIVED,
                    sender = "티바트 시스템"
                )
            )

            isInitializing = false
            placeholderText = "메시지 입력"
        }
    }

    // 채팅방을 나갈 때 채팅 내역 저장 (로딩 메시지 제외)
    // 나중에 리팩토링 필요. 일단 문구 하드코딩으로 필터링
    DisposableEffect(characterId) {
        onDispose {
            if (messages.isNotEmpty()) {
                // 코루틴 컨텍스트 밖에서 호출되므로 GlobalScope 사용
                GlobalScope.launch {
                    // 로딩 메시지 필터링 - "입력 중..."으로 시작하는 메시지 제외
                    val filteredMessages = messages.filter { message ->
                        // 마지막 AI 메시지가 로딩 메시지인 경우 제외
                        !(message.type == MessageType.RECEIVED &&
                                (message.text == "입력 중..." ||
                                        message.text.startsWith("지맥 상태 분석 중") ||
                                        message.text.startsWith("지맥 네트워크에 침투 중") ||
                                        message.text.startsWith("지맥에서 데이터 추출 중") ||
                                        message.text.startsWith("추출한 데이터를 파악하는 중") ||
                                        message.text.startsWith("파악한 데이터를 출력하는 중") ||
                                        message.text.startsWith("오류 확인 중") ||
                                        message.text.startsWith("조금만 더 기다려주세요")))
                    }

                    // 필터링된 메시지만 저장
                    chatHistoryManager.saveChatHistory(characterId, filteredMessages)
                    Log.d("ChatScreen", "Saved ${filteredMessages.size} messages, filtered out loading messages")
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

    // ===== 메시지 전송 함수 ===== (스트리밍 버전) - 수정된 부분
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
            text = "입력 중...",  // 초기 상태 메시지
            timestamp = getCurrentTime(),
            type = MessageType.RECEIVED,
            sender = character.name
        )
        messages.add(aiResponseMessage)

        // 진행 상태 메시지 목록
        val processingMessages = listOf(
            "입력 중...",
            "지맥 상태 분석 중...",
            "지맥 네트워크에 침투 중...",
            "지맥에서 데이터 추출 중...",
            "추출한 데이터를 파악하는 중...",
            "파악한 데이터를 출력하는 중...",
            "조금만 더 기다려주세요..."
        )

        // 진행 상태 업데이트를 위한 Job
        var progressJob: Job? = null

        // API 요청 및 응답 처리 (스트리밍)
        coroutineScope.launch {
            val apiKey = preferencesManager.getApiKey()

            // 진행 상태 업데이트 작업 시작
            progressJob = launch {
                var index = 0
                while (isActive) {
                    delay(6500) // 6.5초마다 메시지 변경
                    if (index < processingMessages.size - 1) {
                        index++
                        // 메시지 리스트의 마지막 메시지 업데이트
                        val updatedMessage = messages.last().copy(text = processingMessages[index])
                        messages[messages.lastIndex] = updatedMessage

                        // 스크롤 유지
                        scrollState.animateScrollToItem(messages.size - 1)
                    } else {
                        // 마지막 메시지 도달 시 더 이상 변경하지 않음
                        break
                    }
                }
            }

            // 세션 복원이 필요한 경우 (저장된 메시지가 있고 첫 메시지를 보내는 경우)
            if (needsSessionRestoration && savedMessagesLoaded) {
                Log.d("ChatScreen", "First message after restore, restoring session...")

                // 이전 대화 내역을 모두 전송하여 컨텍스트 복원
                // 수정된 부분: forceReinitialize를 false로 설정하여 기존 세션을 유지
                val success = GeminiChatService.restoreSession(
                    apiKey = apiKey,
                    characterId = characterId,
                    savedMessages = internalChatHistory,
                    forceReinitialize = false  // 기존 세션 유지
                )

                if (!success) {
                    // 세션 복원 실패 시 사용자에게 알림
                    progressJob?.cancel() // 진행 상태 업데이트 중지
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
                        progressJob?.cancel() // 진행 상태 업데이트 중지
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
                            if (text.isNotBlank()) {
                                // 진행 상태 업데이트 중지 (실제 응답이 오기 시작함)
                                progressJob?.cancel()

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
                    }

                    // 응답 완료 처리
                    if (streamResponse.isComplete) {
                        progressJob?.cancel() // 진행 상태 업데이트 중지
                        isLoading = false
                        placeholderText = "메시지 입력"
                    }
                }

                // 정상 응답인 경우에만 내부 채팅 기록 및 저장 작업 시작
                if (!hasError) {
                    val finalAiMessage = messages.last()

                    // 백그라운드에서 처리할 작업들을 launch로 분리
                    coroutineScope.launch {
                        try {
                            // 내부 채팅 기록 업데이트 (백그라운드에서 처리)
                            val newHistory = internalChatHistory + listOf(
                                userMessage,      // 방금 보낸 사용자 메시지
                                finalAiMessage    // 최종 완성된 AI 응답
                            )

                            /*
                            // 길이제한 코드 지금 당장은 안쓸거임
                            // 길이 제한 (최대 20개 메시지만 유지)
                            internalChatHistory = if (newHistory.size > 20) {
                                newHistory.drop(newHistory.size - 20)
                            } else {
                                newHistory
                            }*/

                            // 채팅 내역 저장 (별도 백그라운드 작업으로 분리) - 로딩 메시지 필터링은 DisposableEffect에서 처리
                            chatHistoryManager.saveChatHistory(characterId, messages)
                        } catch (e: Exception) {
                            Log.e("ChatScreen", "Error in background processing: ${e.message}", e)
                        }
                    }
                }

            } catch (e: Exception) {
                // 예외 발생 시 처리
                progressJob?.cancel() // 진행 상태 업데이트 중지
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
        }
    }

    // ===== UI 렌더링 =====
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = character.name, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "뒤로 가기",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "메뉴",
                            tint = Color.White
                        )
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
                            Text("대화 초기화")
                        }
                    }
                },
                backgroundColor = NavyBlue,
                contentColor = Color.White,
                elevation = 4.dp
            )
        },
        backgroundColor = Color.White
    ) { paddingValues ->
        // 채팅 영역
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 메시지 목록
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
                state = scrollState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 70.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    // 캐릭터 메시지일 경우에만 character 전달
                    if (message.type == MessageType.RECEIVED && message.sender != "티바트 시스템") {
                        MessageItem(message = message, character = character)
                    } else {
                        MessageItem(message = message, character = character)
                    }
                }
            }

            // 메시지 입력 영역 (하단에 고정)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 8.dp, vertical = 9.dp)
                    .imePadding() // 키보드가 올라올 때 입력 필드 위치 조정
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 메시지 입력 필드
                    OutlinedTextField(
                        value = newMessageText,
                        onValueChange = { newMessageText = it },
                        placeholder = { 
                            Text(
                                text = placeholderText, 
                                color = Color.Gray
                            ) 
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .heightIn(min = 56.dp), // 최소 높이만 설정하고 최대 높이는 제한 없음
                        enabled = !isLoading && !isInitializing,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = NavyBlue,
                            unfocusedBorderColor = Color.LightGray,
                            backgroundColor = Color.White,
                            textColor = Color.Black,
                            cursorColor = NavyBlue // 커서 색상 지정
                        ),
                        textStyle = MaterialTheme.typography.body1.copy(
                            fontSize = 16.sp,
                            lineHeight = 24.sp // 줄 간격 넉넉하게 조정
                        ),
                        maxLines = 4, // 최대 4줄까지 표시
                        singleLine = false, // 여러 줄 입력 가능하도록 설정
                        shape = RoundedCornerShape(24.dp)
                    )

                    // 전송 버튼/로딩 인디케이터
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (newMessageText.isNotBlank() && !isLoading && !isInitializing)
                                    NavyBlue
                                else Color.Gray,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading || isInitializing) {
                            // 로딩 중 인디케이터
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = IconTextColor,
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
                                    tint = IconTextColor
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
fun MessageItem(message: Message, character: Character? = null) {
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
        // 발신자 정보 (받은 메시지의 경우에만 프로필 이미지 표시)
        if (message.type == MessageType.RECEIVED && character != null) {
            // 이름 대신 프로필 이미지 표시
            Image(
                painter = painterResource(id = character.profileImage),
                contentDescription = "${character.name} 프로필",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .padding(bottom = 2.dp),
                contentScale = ContentScale.Crop
            )
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
            val bubbleColor = if (message.type == MessageType.SENT) 
                Color(0xFFFFF599) // 노란색 - 보낸 메시지
            else 
                Color(0xFFE8F1F8) // 연한 파란색 - 받은 메시지
            
            val bubbleBorderColor = if (message.type == MessageType.SENT)
                Color(0xFFEFE7A0) // 노란색 테두리 - 보낸 메시지
            else
                Color(0xFFD8E6F1) // 연한 파란색 테두리 - 받은 메시지

            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = if (message.type == MessageType.SENT) 16.dp else 0.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(bubbleColor)
                    .border(
                        width = 0.5.dp,
                        color = bubbleBorderColor,
                        shape = RoundedCornerShape(
                            topStart = if (message.type == MessageType.SENT) 16.dp else 0.dp,
                            topEnd = 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        )
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