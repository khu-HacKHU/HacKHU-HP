# 홈 프론트엔드 명세

홈 기능의 프론트엔드 명세를 읽고 고칠 때 필요한 문서를 안내한다. 담당은 게시판팀이다.

명세 본문을 이 문서에 복사하지 않는다.

<br>

### 1. 문서의 역할

| 문서 | 정하는 것 |
|---|---|
| [1-1-BACKGROUND.md](1-1-BACKGROUND.md) | 이 기능의 화면이 왜 필요한지. 대상 사용자, 이 세트에서 쓰는 용어 |
| [1-2-REQUIREMENTS.md](1-2-REQUIREMENTS.md) | 지원 범위, 반응형과 기기 조건, 접근성과 성능 목표를 측정 가능한 값으로 |
| [2-1-INFORMATION-ARCHITECTURE.md](2-1-INFORMATION-ARCHITECTURE.md) | 화면 목록과 각 화면에 어떤 정보를 어떤 우선순위로 보여 주는지 |
| [2-2-LAYOUT.md](2-2-LAYOUT.md) | 화면별 배치. 간격, 글자, 배치 값을 적는다 |
| [2-3-COMPONENT-ARCHITECTURE.md](2-3-COMPONENT-ARCHITECTURE.md) | 컴포넌트 경계와 책임, 재사용 단위 |
| [2-4-STATE-MANAGEMENT.md](2-4-STATE-MANAGEMENT.md) | 화면 상태와 서버 상태의 구분, 저장 위치, 수명과 복원 |
| [3-1-CONTRACT.md](3-1-CONTRACT.md) | 백엔드와 주고받는 계약 중 이 화면이 쓰는 범위. 기준은 이 기능 backend의 4-2이고 여기서 계약을 새로 정하지 않는다 |
| [3-2-DATA-INTEGRATION.md](3-2-DATA-INTEGRATION.md) | 데이터를 어떻게 받아 어디에 두고 어떻게 갱신하는지 |
| [3-3-COMPOSITION-WIRING.md](3-3-COMPOSITION-WIRING.md) | 화면, 컴포넌트, 상태, 데이터 계층을 어떻게 이어 붙이는지 |
| [3-4-INTERACTION-FLOWS.md](3-4-INTERACTION-FLOWS.md) | 사용자가 시간순으로 거치는 화면 흐름과 분기 |
| [4-1-ERROR-HANDLING.md](4-1-ERROR-HANDLING.md) | 오류별 표시, 재시도, 복귀 경로. 오류 처리는 여기 한곳에 모은다 |
| [4-2-QUALITY.md](4-2-QUALITY.md) | 접근성, 성능. 로그인 상태 처리는 auth/frontend의 4-2를 따른다 |
| [5-VERIFICATION.md](5-VERIFICATION.md) | 테스트 항목, 실행 명령과 통과 증거, 린트. 항목이 있는 것과 실행해 통과한 것을 구분한다 |
| [DECISIONS.md](DECISIONS.md) | 이 세트 결정의 이력 |

- 앞 단계의 결정이 뒤 단계의 기준이다. 구현에 맞추려고 앞 단계를 임의로 바꾸지 않는다.
- 미정이라고 적힌 항목을 추측으로 채우지 않는다.
