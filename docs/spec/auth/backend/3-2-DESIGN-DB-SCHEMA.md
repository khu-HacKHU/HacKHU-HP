# 3-2 DB 스키마

본 문서는 인증권한 기능이 정하는 테이블 `users`, `login_sessions`, `refresh_tokens`의 컬럼, 제약, 인덱스와 데이터 수명을 정한다. 다른 기능은 `users.id`만 외래 키로 참조한다.

<br>

### 3-2-1. 공통

- 시각 컬럼은 모두 `TIMESTAMPTZ`로 저장하고 UTC로 다룬다.
- `users`와 `refresh_tokens`의 기본 키는 `BIGINT GENERATED ALWAYS AS IDENTITY`로 만든다. `login_sessions`의 기본 키는 access 토큰에 실리므로 추측할 수 없는 UUID로 만든다.
- 테이블 이름은 `users`다. `user`는 PostgreSQL 예약어라서 쓰지 않는다.
- 스키마는 Flyway 마이그레이션으로만 만든다. 첫 마이그레이션은 가입 API 이슈(#24)에서 낸다.

<br>

### 3-2-2. users

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| email | VARCHAR(254) | NULL 허용 | 소문자로 바꿔 저장함 <br> 탈퇴하면 NULL |
| name | VARCHAR(30) | NULL 허용 | 앞뒤 공백을 지우고 저장함 <br> 탈퇴하면 NULL |
| password_hash | VARCHAR(100) | NULL 허용 | bcrypt 해시 <br> 탈퇴하면 NULL |
| role | VARCHAR(20) | NOT NULL | `PENDING`, `MEMBER`, `STAFF`, `ADMIN` 중 하나 |
| must_change_password | BOOLEAN | NOT NULL, 기본 false | 비밀번호 변경 필요 상태 |
| temp_password_expires_at | TIMESTAMPTZ | NULL 허용 | 임시 비밀번호 만료 시각 <br> 임시 비밀번호가 아니면 NULL |
| temp_password_used_at | TIMESTAMPTZ | NULL 허용 | 임시 비밀번호로 처음 로그인한 시각 |
| approved_at | TIMESTAMPTZ | NULL 허용 | 가입 승인 시각 |
| withdrawn_at | TIMESTAMPTZ | NULL 허용 | 탈퇴 시각 <br> NULL이면 활성 계정 |
| created_at | TIMESTAMPTZ | NOT NULL, 기본 now() | 가입 시각 |
| updated_at | TIMESTAMPTZ | NOT NULL, 기본 now() | |

**제약**

| 이름 | 내용 | 이유 |
|---|---|---|
| `uk_users_email` | `email`에 대한 유니크 인덱스, `WHERE email IS NOT NULL` | 활성 계정끼리 이메일이 겹치지 않음 <br> 탈퇴하거나 거절된 이메일로 다시 가입할 수 있음 |
| `ck_users_email_lower` | `email = lower(email)` | 대소문자만 다른 이메일로 중복 가입하는 것을 막음 |
| `ck_users_role` | `role IN ('PENDING', 'MEMBER', 'STAFF', 'ADMIN')` | 정의되지 않은 역할을 막음 |
| `ck_users_active_fields` | `withdrawn_at IS NOT NULL OR (email IS NOT NULL AND name IS NOT NULL AND password_hash IS NOT NULL)` | 활성 계정은 로그인 정보가 비지 않음 |
| `ck_users_withdrawn_fields` | `withdrawn_at IS NULL OR (email IS NULL AND name IS NULL AND password_hash IS NULL)` | 탈퇴한 계정에 개인정보가 남지 않음 |

**인덱스**

| 인덱스 | 쓰는 곳 |
|---|---|
| `(role, created_at)` | 승인 대기 목록, 역할별 회원 목록, 활성 관리자 수 확인 |

<br>

### 3-2-3. login_sessions

로그인 한 번이 행 하나다. 기기 하나의 로그인 상태에 해당한다. access 토큰의 `sid` 클레임이 이 행의 ID이고, 요청마다 이 행이 살아 있는지 확인한다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | UUID | PK | 서버가 무작위로 만듦 |
| user_id | BIGINT | NOT NULL, FK `users.id` ON DELETE CASCADE | 로그인한 사람 |
| expires_at | TIMESTAMPTZ | NOT NULL | 로그인 뒤 30일 <br> 회전해도 바뀌지 않음 |
| revoked_at | TIMESTAMPTZ | NULL 허용 | 폐기한 시각 <br> NULL이면 살아 있음 |
| revoke_reason | VARCHAR(30) | NULL 허용 | `LOGOUT`, `PASSWORD_CHANGED`, `PASSWORD_RESET`, `REUSE_DETECTED` 중 하나 |
| created_at | TIMESTAMPTZ | NOT NULL, 기본 now() | 로그인 시각 |

**인덱스**

| 인덱스 | 쓰는 곳 |
|---|---|
| `(user_id)` | 사용자의 모든 로그인 세션 폐기 |
| `(expires_at)` | 오래된 기록 정리 |

요청마다 `login_sessions`와 `users`를 기본 키로 한 번에 읽는다. 세션이 폐기되면 그 세션에서 발급한 access 토큰은 만료 전이라도 다음 요청부터 거부된다. 발급 시각을 비교하지 않으므로 같은 초에 발급되어도 구분된다.

<br>

### 3-2-4. refresh_tokens

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| session_id | UUID | NOT NULL, FK `login_sessions.id` ON DELETE CASCADE | 속한 로그인 세션 |
| token_hash | CHAR(64) | NOT NULL, UNIQUE | refresh 토큰의 SHA-256 해시(16진수) <br> 토큰 원문은 저장하지 않음 |
| expires_at | TIMESTAMPTZ | NOT NULL | 발급 뒤 14일 <br> 세션의 `expires_at`을 넘지 않음 |
| replaced_by_id | BIGINT | NULL 허용, FK `refresh_tokens.id` ON DELETE SET NULL | 회전으로 이 토큰 다음에 발급한 토큰 |
| replaced_at | TIMESTAMPTZ | NULL 허용 | 회전한 시각 |
| revoked_at | TIMESTAMPTZ | NULL 허용 | 응답 유실 복구로 이 토큰만 폐기한 시각 |
| created_at | TIMESTAMPTZ | NOT NULL, 기본 now() | |

**인덱스**

| 인덱스 | 쓰는 곳 |
|---|---|
| `token_hash` 유니크 | refresh 요청에서 토큰 찾기 |
| `(session_id)` | 세션의 현재 토큰 찾기 |
| `(expires_at)` | 오래된 기록 정리 |

- 한 세션에서 `replaced_at`과 `revoked_at`이 모두 NULL인 토큰은 많아야 하나다. 이 토큰을 세션의 현재 토큰이라 부른다.
- refresh 토큰은 32바이트 무작위 값을 Base64URL로 인코딩한 문자열이다. 무작위 값이라 느린 해시가 필요 없고, SHA-256 해시로 바로 찾는다. DB가 유출되어도 해시로는 토큰을 쓸 수 없다.
- 회전과 재사용 판단 규칙은 계약 문서에서 정한다. ([4-2-INTERACTION-CONTRACT.md](4-2-INTERACTION-CONTRACT.md))

<br>

### 3-2-5. 상태 변화

| 사건 | users | login_sessions | refresh_tokens |
|---|---|---|---|
| 가입 | 행 추가, `role = PENDING` | | |
| 처음 관리자 생성 | 행 추가, `role = ADMIN`, `must_change_password = true`, `temp_password_expires_at = NULL` | | |
| 승인 | `role = MEMBER`, `approved_at` 기록 | | |
| 거절 | 행 삭제 | CASCADE로 삭제 | CASCADE로 삭제 |
| 로그인 | 임시 비밀번호였으면 `temp_password_used_at` 기록 | 행 추가 | 첫 토큰 추가 |
| refresh | | | 4-2-5의 규칙대로 회전 |
| 로그아웃 | | 그 세션에 `revoked_at` 기록 | |
| 역할 변경 | `role` 변경 | | |
| 비밀번호 변경 | `password_hash` 교체, `must_change_password = false`, 임시 비밀번호 컬럼 비움 | 그 사용자의 살아 있는 세션 모두 폐기 후 새 세션 추가 | 새 세션의 첫 토큰 추가 |
| 임시 비밀번호 발급 | `password_hash`를 임시 비밀번호 해시로 교체, `must_change_password = true`, `temp_password_expires_at = now() + 24시간`, `temp_password_used_at = NULL` | 대상의 살아 있는 세션 모두 폐기 | |
| 탈퇴 | `email`, `name`, `password_hash`를 NULL로, `withdrawn_at` 기록 | 그 사용자의 행 모두 삭제 | CASCADE로 삭제 |

<br>

### 3-2-6. 다른 기능의 참조

- 글, 첨부파일, 앨범의 작성자 컬럼은 `users.id`를 외래 키로 참조한다. 삭제 규칙은 기본값(`NO ACTION`)으로 둔다. 활성 계정과 탈퇴한 계정의 행은 지우지 않으므로 참조가 깨지지 않는다.
- 행을 지우는 경우는 승인 대기 계정의 거절뿐이다. 승인 대기 회원은 글과 앨범을 만들 수 없어서 참조하는 행이 없다.
- 탈퇴한 작성자를 "탈퇴한 회원"으로 보여 주는 방법은 3-1에서 정한다. ([3-1-DESIGN-SOFTWARE-ARCHITECTURE.md](3-1-DESIGN-SOFTWARE-ARCHITECTURE.md))

<br>

### 3-2-7. 데이터 정리

- 매일 한 번 `expires_at`이나 `revoked_at`이 하루보다 오래된 `login_sessions` 행을 지운다. 그 세션의 `refresh_tokens`는 CASCADE로 함께 지워진다.
- 같은 때 `expires_at`, `replaced_at`, `revoked_at` 중 하나가 하루보다 오래된 `refresh_tokens` 행을 지운다. 지운 토큰이 다시 오면 모르는 토큰으로 보고 401을 준다.
- `users`는 자동으로 지우지 않는다. 보존 기간은 요구사항 문서를 따른다. ([2-REQUIREMENTS.md](2-REQUIREMENTS.md))
