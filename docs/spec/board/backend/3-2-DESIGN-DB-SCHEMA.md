# 3-2 DB 스키마

본 문서는 게시판 기능이 정하는 테이블 `post`, `attachment`의 컬럼, 제약, 인덱스와 데이터 수명을 정한다. 회원 테이블 `users`는 인증권한팀이, 업로드 발급 기록은 `global/storage`가 정하고 여기서는 참조만 한다. ([auth 3-2](../../auth/backend/3-2-DESIGN-DB-SCHEMA.md), [gallery 3-2](../../gallery/backend/3-2-DESIGN-DB-SCHEMA.md))

<br>

### 3-2-1. 공통

- 시각 컬럼은 모두 `TIMESTAMPTZ`로 저장하고 UTC로 다룬다. 엔티티에서는 `Instant`를 쓴다.
- 기본 키는 `BIGINT GENERATED ALWAYS AS IDENTITY`로 만든다. 순차 ID라서 ID를 안다고 접근할 수 있으면 안 된다. 권한 검사는 보안 문서를 따른다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))
- 스키마는 Flyway 마이그레이션으로만 만든다. `post`는 공지 조회 API 이슈(#29)에서, `attachment`와 게시판 종류 추가는 자료게시판 API 이슈(#55)와 자유게시판 API 이슈(#53)에서 낸다.
- 세 게시판이 테이블 하나를 같이 쓰고 `board_type`으로 구분한다. 이유와 대안은 결정 기록에 있다. ([DECISIONS.md](DECISIONS.md) B1)
- 테이블 이름은 레포 규칙대로 단수형으로 쓴다. 본문 컬럼과 필드 이름은 백엔드 규칙의 예시(`post.edit(title, content)`)에 맞춰 `content`로 쓴다. ([code-style.md](../../../conventions/code-style.md), [backend/AGENTS.md](../../../../backend/AGENTS.md))

<br>

### 3-2-2. post

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| board_type | VARCHAR(20) | NOT NULL | 게시판 종류 <br> `NOTICE`, `FREE`, `ARCHIVE` 중 하나 <br> 바꾸지 않음 <br> 첫 마이그레이션(#29)은 `NOTICE`만 받고 `FREE`, `ARCHIVE`는 #53, #55에서 더함 |
| title | VARCHAR(100) | NOT NULL | 앞뒤 공백을 지우고 저장함 |
| content | TEXT | NOT NULL | 받은 그대로 저장함 <br> 줄바꿈은 `\n`으로 맞춰 저장함 |
| author_id | BIGINT | NOT NULL, FK `users.id` | 작성자 <br> 수정해도 바뀌지 않음 |
| created_at | TIMESTAMPTZ | NOT NULL, 기본 now() | 작성 시각 |
| updated_at | TIMESTAMPTZ | NOT NULL, 기본 now() | 마지막 수정 시각 <br> 수정한 적이 없으면 `created_at`과 같음 <br> 트리거가 갱신함 |

**제약**

| 이름 | 내용 | 이유 |
|---|---|---|
| `ck_post_board_type` | `board_type IN ('NOTICE', 'FREE', 'ARCHIVE')` | 정의되지 않은 게시판 종류를 막음 |
| `ck_post_title_length` | `char_length(title) BETWEEN 1 AND 100` | 빈 제목을 막음 <br> 애플리케이션 검사를 빠뜨려도 DB가 막음 |
| `ck_post_content_length` | `char_length(content) BETWEEN 1 AND 10000` | 빈 본문과 지나치게 긴 본문을 막음 |
| `fk_post_author` | `author_id`가 `users.id`를 참조, 삭제 규칙은 기본값(`NO ACTION`) | 작성자 행이 지워지면 글이 고아가 되는 것을 막음 |

**트리거**

| 이름 | 내용 | 이유 |
|---|---|---|
| `trg_post_set_updated_at` | `BEFORE UPDATE`마다 `set_updated_at()`을 실행해 `updated_at = now()` | `updated_at`은 DB 트리거가 갱신하는 레포 규칙을 따름 <br> 함수는 `users` 마이그레이션이 만듦 ([backend/AGENTS.md](../../../../backend/AGENTS.md), [auth DECISIONS.md](../../auth/backend/DECISIONS.md) A19) |

- 코드에서 `created_at`과 `updated_at`을 직접 쓰지 않는다. 엔티티는 저장한 뒤 DB 값을 다시 읽는다.
- `set_updated_at()` 함수가 없으면 마이그레이션이 실패한다. 그래서 `post` 마이그레이션은 `users` 마이그레이션(#24) 뒤 번호로 낸다.
- 자료게시판 글에서 첨부파일만 바꿔도 `post`의 `updated_at`을 갱신한다. 같은 트랜잭션에서 `post` 행을 잠그며 갱신하므로 트리거가 시각을 바꾼다. (5-1-8)

길이 값은 요구사항 문서를 따른다. ([2-REQUIREMENTS.md](2-REQUIREMENTS.md)) PostgreSQL의 `char_length`는 코드 포인트 단위로 센다. Java의 `String.length()`는 UTF-16 단위라서 이모지 같은 문자를 두 자로 센다. 애플리케이션 검사는 `codePointCount`로 해서 두 결과를 맞춘다.

**인덱스**

| 인덱스 | 쓰는 곳 |
|---|---|
| `(board_type, created_at DESC, id DESC)` | 게시판별 최신순 목록 |
| `(author_id)` | 작성자의 글 찾기 <br> 외래 키 참조 확인 |

<br>

### 3-2-3. attachment

자료게시판 글의 첨부파일 하나가 행 하나다.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| post_id | BIGINT | NOT NULL, FK `post.id` ON DELETE CASCADE | 첨부한 글 <br> 자료게시판 글만 가리킴 |
| object_key | VARCHAR(255) | NOT NULL, UNIQUE | S3 객체 키 <br> 형식 `board/{uuid}.{확장자}`, 확장자는 형식 확인을 통과한 실제 형식 기준 <br> 응답에 넣지 않음 |
| file_name | VARCHAR(200) | NOT NULL | 원래 파일 이름 <br> 경로 부분과 제어 문자를 지우고 저장함 (5-1-6) |
| content_type | VARCHAR(100) | NOT NULL | 형식 확인을 통과한 MIME 타입 |
| size_bytes | BIGINT | NOT NULL | 파일 크기 |
| display_order | SMALLINT | NOT NULL | 0부터 시작하는 표시 순서 |
| created_at | TIMESTAMPTZ | NOT NULL, 기본 now() | 첨부한 시각 |

**제약**

| 이름 | 내용 | 이유 |
|---|---|---|
| `uq_attachment_post_display_order` | `UNIQUE (post_id, display_order)` | 같은 글 안에서 순서가 겹치지 않음 |
| `ck_attachment_content_type` | `content_type IN ('application/pdf', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', 'application/vnd.openxmlformats-officedocument.presentationml.presentation', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')` | 허용하지 않는 형식이 저장되지 않음 |
| `ck_attachment_size` | `size_bytes BETWEEN 1 AND 20971520` | 빈 파일과 20MB 초과 파일이 저장되지 않음 |
| `ck_attachment_display_order` | `display_order BETWEEN 0 AND 4` | 순서 범위로 글 하나 최대 5개를 한 번 더 막음 |

**인덱스**

| 인덱스 | 쓰는 곳 |
|---|---|
| `object_key` 유니크 | 같은 객체를 두 글이 가리키지 않음 |
| `(post_id, display_order)` 유니크 | 글의 첨부파일 목록, 첨부 수 세기 |

- 글 하나 최대 5개는 애플리케이션이 먼저 검사하고, `display_order` 범위와 유니크 제약이 한 번 더 막는다. 동시에 수정해도 6개가 되지 않게 글 행을 잠근 뒤 센다. (5-1-8)
- 첨부파일이 자료게시판 글에만 붙는지는 DB 제약으로 걸 수 없다. 다른 테이블의 값을 확인하려면 트리거가 필요하기 때문이다. 첨부를 만드는 API가 자료게시판 API뿐이고 서비스가 글의 `board_type`을 확인하므로 애플리케이션에서 지킨다.
- 업로드 발급 기록(`uploadId`)은 저장하지 않는다. 확정할 때 발급 기록에서 객체 키, 형식, 크기를 옮겨 적고 기록은 사용 처리한다.

<br>

### 3-2-4. 상태 변화

| 사건 | post | attachment | S3 객체 |
|---|---|---|---|
| 공지, 자유게시판 글 작성 | 행 추가, `author_id`는 현재 사용자 <br> `created_at`과 `updated_at`은 같은 값 | | |
| 자료게시판 글 작성 | 행 추가 | 확정한 업로드마다 행 추가 | 업로드한 객체를 그대로 씀 |
| 글 수정 | `title`, `content` 교체, 트리거가 `updated_at` 갱신 <br> `board_type`, `author_id`, `created_at`은 바꾸지 않음 | 자료게시판이면 뺀 첨부 행 삭제, 새 첨부 행 추가, 남는 첨부의 `display_order` 다시 매김 | 뺀 첨부의 객체를 트랜잭션 커밋 뒤 삭제 |
| 글 삭제 | 행 삭제 | CASCADE로 삭제 | 트랜잭션 커밋 뒤 첨부 객체 모두 삭제 |
| 작성자 탈퇴 | 바뀌지 않음 <br> `users` 행이 익명화되어 남음 | 바뀌지 않음 | 바뀌지 않음 |
| 작성자 역할 변경 | 바뀌지 않음 | 바뀌지 않음 | 바뀌지 않음 |

<br>

### 3-2-5. 다른 기능과의 관계

- `author_id`는 `users.id`를 참조한다. 활성 계정과 탈퇴한 계정의 행은 지우지 않으므로 참조가 깨지지 않는다. 승인 대기 회원은 글을 쓸 수 없어서 거절로 지워지는 행을 참조하는 글이 없다. (auth 3-2-DESIGN-DB-SCHEMA.md 3-2-6)
- `post` 마이그레이션은 `users` 마이그레이션(#24) 뒤에 낸다. `set_updated_at()` 함수와 외래 키 대상이 모두 `users` 마이그레이션에 있기 때문이다. 번호는 낼 때 레포의 마지막 번호 다음으로 정한다. (작성 시점 기준 `V1`은 `users`(PR #106), `V2`는 갤러리(PR #107)로 열려 있음)
- 작성자 이름은 `users.name`을 조회 시점에 읽는다. 글에 이름을 복사해 두지 않는다. 탈퇴하면 이름이 NULL이 되고 응답에서 "탈퇴한 회원"으로 보이게 된다. 응답 형식은 계약 문서를 따른다. ([4-2-INTERACTION-CONTRACT.md](4-2-INTERACTION-CONTRACT.md))
- 업로드 발급 기록은 `global/storage`의 테이블이다. 게시판은 발급할 때 용도를 `BOARD_ATTACHMENT`로 남기고, 확정할 때 "이 사용자에게 이 용도로 발급했고 아직 쓰지 않았고 만료되지 않았는가"를 확인한다. 테이블 구성은 갤러리팀과 PL이 정한다. ([gallery 3-2](../../gallery/backend/3-2-DESIGN-DB-SCHEMA.md) 4절, #40)
- 메인 화면의 최근 공지는 이 테이블을 `NOTICE` 조건으로 읽는다. 홈 기능에서 다룬다. ([홈 배경](../../home/BACKGROUND.md))

<br>

### 3-2-6. 데이터 정리

- 자동으로 지우는 글은 없다. 보존 기간은 요구사항 문서를 따른다. ([2-REQUIREMENTS.md](2-REQUIREMENTS.md))
- 삭제는 행을 바로 지운다. 지운 글을 따로 보관하지 않는다. 이유와 대안은 결정 기록에 있다. ([DECISIONS.md](DECISIONS.md) B3)
- 첨부파일의 S3 객체는 DB 트랜잭션이 커밋된 뒤 같은 요청 안에서 지운다. 커밋 전에 지우면 롤백되었을 때 DB 행은 남고 파일만 사라지기 때문이다.
- S3 삭제가 실패하면 응답은 성공으로 주고 객체 키와 함께 경고 로그를 남긴다. DB에서는 이미 지워져 다운로드 주소를 발급할 수 없으므로 사용자에게는 지워진 것과 같다. 남은 객체는 로그를 보고 지운다. ([DECISIONS.md](DECISIONS.md) B8)
- 확정하지 않은 업로드의 객체는 `global/storage`의 정리 작업이 지운다. 게시판에서 따로 만들지 않는다.
