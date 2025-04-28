# 캐릭터 관리 구조 리팩토링 결과

## 리팩토링 목적

- 새로운 캐릭터를 쉽게 추가할 수 있는 구조로 변경
- 코드의 재사용성 및 유지보수성 향상
- 캐릭터 데이터와 관련 로직의 중앙 집중화

## 주요 변경 사항

### 1. Character 클래스 확장
- `Character` 데이터 클래스에 `description`과 `prompt` 필드 추가
- 아이디, 이름, 설명, 프로필 이미지, 프롬프트를 하나의 클래스로 관리

### 2. CharacterRepository 클래스 생성
- 캐릭터 데이터와 프롬프트를 중앙에서 관리하는 저장소 패턴 적용
- 모든 캐릭터 정보 및 프롬프트를 한 곳에서 관리
- `getAllCharacters()`, `getCharacterById()`, `getCharacterPrompt()` 등의 유틸리티 메서드 제공

### 3. ChatRoom 클래스 수정
- `Character` 정보를 활용하도록 수정
- `id`, `name`, `profileImage` 필드 대신 `characterId` 하나로 참조
- `getCharacter()` 메서드를 통해 해당 캐릭터 정보 접근

### 4. ChatListScreen 컴포넌트 개선
- 하드코딩된 캐릭터 목록 대신 `CharacterRepository`에서 캐릭터 목록 가져오기
- 캐릭터 데이터로부터 채팅방 목록 생성

### 5. GeminiChatService 수정
- 하드코딩된 캐릭터 프롬프트 제거
- `CharacterRepository`에서 프롬프트 가져오도록 수정
- 프롬프트 캐싱 로직 유지하면서 더 유연한 구조로 변경

### 6. 새 캐릭터 추가 가이드 문서화
- `README_CHARACTER_GUIDE.md` 파일 생성
- 새 캐릭터 추가를 위한 단계별 가이드 제공

## 리팩토링 이후 새 캐릭터 추가 방법

새 캐릭터를 추가하려면 다음 단계만 수행하면 됩니다:

1. 캐릭터 이미지를 `drawable` 디렉토리에 추가
2. `CharacterRepository.kt` 파일에서:
   - `CHARACTER_PROMPTS` 맵에 새 프롬프트 추가
   - `characters` 리스트에 새 캐릭터 정보 추가

기존에는 여러 파일을 수정해야 했지만, 이제는 `CharacterRepository.kt` 파일 하나만 수정하면 됩니다.

## 향후 개선 사항

- 캐릭터 정보를 JSON 파일로 외부화하여 코드 수정 없이 캐릭터 추가 가능하도록 개선
- 사용자 정의 캐릭터 추가 기능 구현
- 캐릭터 카테고리 분류 기능 추가 