# 코드 컨벤션

스택과 무관한 공통 규칙이다. 프레임워크가 정해지면 언어별 규칙을 아래 "스택별 규칙"에 추가하고, 포매터·린터를 CI에 연결한다.

## 원칙

- **포매팅은 도구가 한다.** 사람은 포매팅을 리뷰하지 않는다. 포매터를 정하면 CI에서 검사한다.
- **읽는 사람 기준으로 쓴다.** 다음 라운드에 다른 팀원이 이 코드의 취약점을 찾고 고친다.
- 주석은 "무엇"이 아니라 "왜"를 적는다. 특히 보안 때문에 일부러 한 선택은 반드시 주석으로 남긴다.

## 공통

| 항목 | 규칙 |
|---|---|
| 인코딩·줄바꿈 | UTF-8, LF ([.editorconfig](../../.editorconfig), [.gitattributes](../../.gitattributes)) |
| 들여쓰기 | 2칸. Java·Kotlin·Python은 4칸 |
| 파일 끝 | 빈 줄 하나 |
| 언어 | 코드 식별자는 영어, 주석·문서·커밋은 한국어 |
| TODO | `TODO(깃허브ID): 내용 #이슈번호` — 주인 없는 TODO를 남기지 않는다 |

## 네이밍

| 대상 | 규칙 | 예 |
|---|---|---|
| URL 경로 | kebab-case, 복수형 명사 | `/api/v1/gallery-photos` |
| JSON 필드 | camelCase | `createdAt`, `authorId` |
| DB 테이블·컬럼 | snake_case, 테이블은 단수형 | `post`, `created_at` |
| 환경변수 | UPPER_SNAKE_CASE | `DATABASE_URL`, `JWT_SECRET` |
| 브랜치 | `{type}/{issue}-{kebab-slug}` | `feat/12-notice-list` |
| boolean | `is`/`has`/`can` 접두사 | `isPinned`, `canEdit` |

## 디렉터리

- 기능(도메인) 단위로 묶는다: `auth/`, `post/`, `gallery/` 아래에 계층을 둔다. 계층 단위(`controllers/`, `services/`)로 먼저 나누지 않는다.
- 도메인에 속하지 않는 공통 코드(에러 처리, 인증 필터, 설정)는 `global/` 또는 `common/` 한 곳에 모은다.
- 팀 간 경계를 넘는 import는 공개된 인터페이스만 쓴다. 다른 팀 도메인의 내부 구현을 직접 부르지 않는다.

## 테스트

- 동작을 바꾸는 PR에는 테스트를 함께 넣는다.
- **권한 검사가 있는 API는 "권한 없는 사용자가 거부되는지"를 테스트한다.** 성공 케이스만 테스트하지 않는다. S4 보안 회귀 테스트의 기반이 된다.

## 스택별 규칙

- frontend: 미정
- backend: [backend/CLAUDE.md](../../backend/CLAUDE.md) — 포매터는 Spotless(google-java-format AOSP 스타일), `./gradlew spotlessApply`
