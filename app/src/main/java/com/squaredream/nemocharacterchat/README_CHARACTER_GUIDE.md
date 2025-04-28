# 새 캐릭터 추가 가이드

이 문서는 NemoCharacterChat 앱에 새 캐릭터를 추가하는 방법을 안내합니다.

## 1. 캐릭터 이미지 준비

1. 새 캐릭터의 프로필 이미지를 준비합니다.
2. 이미지를 `app/src/main/res/drawable/` 디렉토리에 추가합니다.
   - 파일명은 소문자와 영문만 사용하고, 공백 없이 작성합니다. (예: `klee.jpg`)
   - 이미지 사이즈는 정사각형 비율로 준비하는 것을 권장합니다.

## 2. 캐릭터 프롬프트 작성

1. 캐릭터의 채팅 AI 프롬프트를 작성합니다.
2. 프롬프트 작성 시 기존 캐릭터의 프롬프트를 참고하여 작성하는 것을 권장합니다.
3. 프롬프트는 최대한 캐릭터의 특성과 말투를 살려야 합니다.

## 3. CharacterRepository에 캐릭터 추가

`app/src/main/java/com/squaredream/nemocharacterchat/data/CharacterRepository.kt` 파일을 열고 다음 부분을 수정합니다:

1. CHARACTER_PROMPTS 맵에 새 프롬프트 추가:

```kotlin
private val CHARACTER_PROMPTS = mapOf(
    // 기존 캐릭터 프롬프트
    "raiden" to """
        // 라이덴 프롬프트
    """.trimIndent(),
    
    "furina" to """
        // 푸리나 프롬프트
    """.trimIndent(),
    
    // 새 캐릭터 프롬프트 추가
    "klee" to """
        별도의 웹 검색 없이 작업하세요.
        우선 당신이 알고 있는 원신 게임 세계관을 한국어 공식 표기를 기준으로 떠올리세요.
        추가적으로 원신에 등장하는 클레의 정보와 페르소나를 한국어 공식 표기를 기준으로 떠올리세요.
        
        // 여기에 캐릭터 프롬프트 작성
        // 기존 프롬프트를 참고하여 작성
    """.trimIndent()
)
```

2. characters 리스트에 새 캐릭터 추가:

```kotlin
private val characters = listOf(
    // 기존 캐릭터
    Character(
        id = "raiden",
        name = "라이덴 쇼군",
        description = "이나즈마성 천수각에서 만날 수 있는 번개 신",
        profileImage = R.drawable.raiden,
        prompt = CHARACTER_PROMPTS["raiden"] ?: ""
    ),
    Character(
        id = "furina",
        name = "푸리나",
        description = "에피클레스 오페라 하우스에서 만날 수 있는 물의 신?",
        profileImage = R.drawable.furina,
        prompt = CHARACTER_PROMPTS["furina"] ?: ""
    ),
    
    // 새 캐릭터 추가
    Character(
        id = "klee", // 고유 ID (영문 소문자)
        name = "클레", // 표시할 이름
        description = "몬드성에서 만날 수 있는 폭발의 기사", // 설명
        profileImage = R.drawable.klee, // 리소스 ID
        prompt = CHARACTER_PROMPTS["klee"] ?: "" // 프롬프트
    )
)
```

## 4. 앱 빌드 및 테스트

1. 앱을 빌드하고 실행합니다.
2. 채팅 목록 화면에서 새로 추가한 캐릭터가 정상적으로 표시되는지 확인합니다.
3. 캐릭터와의 채팅이 의도한 대로 동작하는지 테스트합니다.

## 주의사항

- 캐릭터 ID는 고유해야 합니다.
- 프롬프트는 충분히 상세하게 작성해야 캐릭터의 특성이 잘 드러납니다.
- 이미지는 저작권에 문제가 없는 것을 사용해야 합니다.
- 리팩토링 이후에는 `GeminiChatService.kt`에 직접 프롬프트를 추가할 필요가 없습니다. 