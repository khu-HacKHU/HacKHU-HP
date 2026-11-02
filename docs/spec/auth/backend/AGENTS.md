# 인증권한 백엔드 명세

인증권한 기능의 백엔드 명세를 읽고 고칠 때 필요한 문서를 안내한다. 담당은 인증권한팀이다.

명세 본문을 이 문서에 복사하지 않는다.

<br>

### 1. 문서의 역할

| 문서 | 정하는 것 |
|---|---|
| [1-BACKGROUND.md](1-BACKGROUND.md) | 이 기능에 어떤 백엔드가 왜 필요한지. 기능 배경, 관련 정책, 이 세트에서 쓰는 용어 |
| [2-REQUIREMENTS.md](2-REQUIREMENTS.md) | 프론트엔드가 백엔드에 기대하는 것. 응답 시간, 처리량, 보존 기간 같은 제약을 측정 가능한 값으로 |
| [3-1-DESIGN-SOFTWARE-ARCHITECTURE.md](3-1-DESIGN-SOFTWARE-ARCHITECTURE.md) | 구성 요소, 패키지 구조, 의존 방향, 도메인 모델. 공통 패키지 규칙은 레포의 backend/AGENTS.md를 따른다 |
| [3-2-DESIGN-DB-SCHEMA.md](3-2-DESIGN-DB-SCHEMA.md) | 이 기능이 정하는 테이블, 컬럼, 인덱스, 제약과 데이터 수명. 다른 기능의 테이블은 참조만 한다 |
| [3-3-DESIGN-CONVENTIONS.md](3-3-DESIGN-CONVENTIONS.md) | 이 기능에만 있는 이름, 예외, 트랜잭션, 로그 규칙. 공통 규칙은 레포의 backend/AGENTS.md에 있으므로 반복하지 않는다 |
| [4-1-INTERACTION-PIPELINES.md](4-1-INTERACTION-PIPELINES.md) | 요청 수신부터 응답까지 처리 순서와 단계 사이에 오가는 데이터 |
| [4-2-INTERACTION-CONTRACT.md](4-2-INTERACTION-CONTRACT.md) | 엔드포인트, 요청과 성공 응답의 실제 예시, 필수와 선택 필드. 공통 형식과 실패 응답 형식은 docs/conventions/api.md를 따르고, 이 기능의 에러 코드와 403, 404 선택은 5-3에서 정한다 |
| [5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md) | API별 인증 수준과 권한 검사, 입력 검증, 개인정보, 위협 모델링. 공통 규칙은 docs/security/secure-coding.md를 따른다 |
| [5-2-QUALITY-CONCURRENCY.md](5-2-QUALITY-CONCURRENCY.md) | 동시 요청, 중복 처리, 잠금, 순서 보장 |
| [5-3-QUALITY-ERROR-HANDLING.md](5-3-QUALITY-ERROR-HANDLING.md) | 이 기능의 에러 코드와 상태, 실패별 처리. 오류 처리는 여기 한곳에 모은다 |
| [5-4-QUALITY-OBSERVABILITY.md](5-4-QUALITY-OBSERVABILITY.md) | 로그에 남길 것과 남기지 않을 것, 지표, 알림 기준 |
| [6-RELEASE-TEST-CASES.md](6-RELEASE-TEST-CASES.md) | 테스트 케이스, 실행 명령과 통과 증거. 권한 거부 케이스를 포함한다. 케이스가 있는 것과 실행해 통과한 것을 구분한다 |
| [DECISIONS.md](DECISIONS.md) | 이 세트 결정의 이력 |

- 앞 단계의 결정이 뒤 단계의 기준이다. 구현에 맞추려고 앞 단계를 임의로 바꾸지 않는다.
- 미정이라고 적힌 항목을 추측으로 채우지 않는다.
