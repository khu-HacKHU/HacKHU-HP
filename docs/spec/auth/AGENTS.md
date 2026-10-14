# 인증권한

가입, 로그인, 가입 승인, 역할 변경을 다룬다. 구현은 인증권한팀이 맡는다. 무엇을 만드는지는 [배경](BACKGROUND.md)과 [사용자 스토리](USER-STORIES.md)가 정하고, 세트 폴더는 어떻게 만드는지를 정한다.

<br>

### 1. 문서

| 문서 | 다루는 것 |
|---|---|
| [BACKGROUND.md](BACKGROUND.md) | 이 기능이 왜 필요한지, 범위 |
| [USER-STORIES.md](USER-STORIES.md) | 누가 무엇을 할 수 있는지, 완료 조건 |
| [backend/](backend/AGENTS.md) | 데이터, API 계약, 권한 검사, 오류 처리 |
| [frontend/](frontend/AGENTS.md) | 화면, 화면 흐름, 상태, 백엔드 연동 |

- 계약은 backend의 `4-2`가 기준이고 frontend는 참조한다. 두 세트에 같은 내용을 복사하지 않는다.
- 형식, 크기, 개수 같은 값은 USER-STORIES.md가 기준이다. 세트 문서는 값을 다시 적지 않고 참조한다.
