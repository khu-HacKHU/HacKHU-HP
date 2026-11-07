# 3-2 DB 스키마

본 문서는 게시판 기능이 정하는 테이블 `posts`의 컬럼, 제약, 인덱스와 데이터 수명을 정한다. 이번 판은 공지만 다룬다. 회원 테이블 `users`는 인증권한팀이 정하고 여기서는 참조만 한다. ([auth 3-2](../../auth/backend/3-2-DESIGN-DB-SCHEMA.md))

<br>

### 3-2-1. 공통

- 시각 컬럼은 모두 `TIMESTAMPTZ`로 저장하고 UTC로 다룬다. 엔티티에서는 `Instant`를 쓴다.
- 기본 키는 `BIGINT GENERATED ALWAYS AS IDENTITY`로 만든다. 순차 ID라서 ID를 안다고 접근할 수 있으면 안 된다. 권한 검사는 보안 문서를 따른다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))
- 스키마는 Flyway 마이그레이션으로만 만든다. 첫 마이그레이션은 공지 조회 API 이슈(#29)에서 낸다.
- 세 게시판이 테이블 하나를 같이 쓰고 `board_type`으로 구분한다. 이유와 대안은 결정 기록에 있다. ([DECISIONS.md](DECISIONS.md) B1)

<br>

### 3-2-2. posts

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| board_type | VARCHAR(20) | NOT NULL | 게시판 종류 <br> 이번 판에서는 `NOTICE`만 있음 |
| title | VARCHAR(100) | NOT NULL | 앞뒤 공백을 지우고 저장함 |
| body | TEXT | NOT NULL | 받은 그대로 저장함 <br> 줄바꿈은 `\n`으로 맞춰 저장함 |
| author_id | BIGINT | NOT NULL, FK `users.id` | 작성자 <br> 수정해도 바뀌지 않음 |
| created_at | TIMESTAMPTZ | NOT NULL, 기본 now() | 작성 시각 |
| updated_at | TIMESTAMPTZ | NOT NULL, 기본 now() | 마지막 수정 시각 <br> 수정한 적이 없으면 `created_at`과 같음 <br> 트리거가 갱신함 |

**제약**

| 이름 | 내용 | 이유 |
|---|---|---|
| `ck_posts_board_type` | `board_type IN ('NOTICE')` | 정의되지 않은 게시판 종류를 막음 <br> #31에서 값을 더함 |
| `ck_posts_title_length` | `char_length(title) BETWEEN 1 AND 100` | 빈 제목을 막음 <br> 애플리케이션 검사를 빠뜨려도 DB가 막음 |
| `ck_posts_body_length` | `char_length(body) BETWEEN 1 AND 10000` | 빈 본문과 지나치게 긴 본문을 막음 |
| `fk_posts_author` | `author_id`가 `users.id`를 참조, 삭제 규칙은 기본값(`NO ACTION`) | 작성자 행이 지워지면 글이 고아가 되는 것을 막음 |

**트리거**

| 이름 | 내용 | 이유 |
|---|---|---|
| `trg_posts_set_updated_at` | `BEFORE UPDATE`마다 `set_updated_at()`을 실행해 `updated_at = now()` | `updated_at`은 DB 트리거가 갱신하는 레포 규칙을 따름 <br> 함수는 `users` 마이그레이션이 만듦 ([backend/AGENTS.md](../../../../backend/AGENTS.md), [auth DECISIONS.md](../../auth/backend/DECISIONS.md) A19) |

- 코드에서 `created_at`과 `updated_at`을 직접 쓰지 않는다. 엔티티는 저장한 뒤 DB 값을 다시 읽는다.
- `set_updated_at()` 함수가 없으면 마이그레이션이 실패한다. 그래서 `posts` 마이그레이션은 `users` 마이그레이션(#24) 뒤 번호로 낸다.

길이 값은 요구사항 문서를 따른다. ([2-REQUIREMENTS.md](2-REQUIREMENTS.md)) PostgreSQL의 `char_length`는 코드 포인트 단위로 센다. Java의 `String.length()`는 UTF-16 단위라서 이모지 같은 문자를 두 자로 센다. 애플리케이션 검사는 `codePointCount`로 해서 두 결과를 맞춘다.

**인덱스**

| 인덱스 | 쓰는 곳 |
|---|---|
| `(board_type, created_at DESC, id DESC)` | 게시판별 최신순 목록 |
| `(author_id)` | 작성자의 글 찾기 <br> 외래 키 참조 확인 |

<br>

### 3-2-3. 상태 변화

| 사건 | posts |
|---|---|
| 공지 작성 | 행 추가, `board_type = NOTICE`, `author_id`는 현재 사용자 <br> `created_at`과 `updated_at`은 같은 값 |
| 공지 수정 | `title`, `body` 교체, 트리거가 `updated_at` 갱신 <br> `board_type`, `author_id`, `created_at`은 바꾸지 않음 |
| 공지 삭제 | 행 삭제 |
| 작성자 탈퇴 | 바뀌지 않음 <br> `users` 행이 익명화되어 남음 |
| 작성자 역할 변경 | 바뀌지 않음 |

<br>

### 3-2-4. 다른 기능과의 관계

- `author_id`는 `users.id`를 참조한다. 활성 계정과 탈퇴한 계정의 행은 지우지 않으므로 참조가 깨지지 않는다. 승인 대기 회원은 공지를 쓸 수 없어서 거절로 지워지는 행을 참조하는 글이 없다. (auth 3-2-DESIGN-DB-SCHEMA.md 3-2-6)
- `posts` 마이그레이션은 `users` 마이그레이션(#24) 뒤에 낸다. `set_updated_at()` 함수와 외래 키 대상이 모두 `users` 마이그레이션에 있기 때문이다. 번호는 낼 때 레포의 마지막 번호 다음으로 정한다. (작성 시점 기준 `V1`은 `users`(PR #106), `V2`는 갤러리(PR #107)로 열려 있음)
- 작성자 이름은 `users.name`을 조회 시점에 읽는다. 글에 이름을 복사해 두지 않는다. 탈퇴하면 이름이 NULL이 되고 응답에서 "탈퇴한 회원"으로 보이게 된다. 응답 형식은 계약 문서를 따른다. ([4-2-INTERACTION-CONTRACT.md](4-2-INTERACTION-CONTRACT.md))
- 메인 화면의 최근 공지는 이 테이블을 읽는다. 홈 기능에서 다룬다. ([홈 배경](../../home/BACKGROUND.md))

<br>

### 3-2-5. 데이터 정리

- 자동으로 지우는 행은 없다. 보존 기간은 요구사항 문서를 따른다. ([2-REQUIREMENTS.md](2-REQUIREMENTS.md))
- 삭제는 행을 바로 지운다. 지운 글을 따로 보관하지 않는다. 이유와 대안은 결정 기록에 있다. ([DECISIONS.md](DECISIONS.md) B3)
