# 3-2 DB 스키마

본 문서는 인증권한 기능이 정하는 테이블 `users`와 `refresh_tokens`의 컬럼, 제약, 인덱스와 데이터 수명을 정한다. 다른 기능은 `users.id`만 외래 키로 참조한다.

<br>

### 3-2-1. 공통

- 시각 컬럼은 모두 `TIMESTAMPTZ`로 저장하고 UTC로 다룬다.
- 기본 키는 `BIGINT GENERATED ALWAYS AS IDENTITY`로 만든다.
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
| tokens_valid_after | TIMESTAMPTZ | NOT NULL, 기본 now() | 이 시각보다 먼저 발급된 access 토큰을 거부함 <br> JWT의 발급 시각이 초 단위라서 초 단위로 내림해 저장함 |
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

### 3-2-3. refresh_tokens

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL, FK `users.id` ON DELETE CASCADE | 토큰 주인 |
| family_id | UUID | NOT NULL | 로그인 묶음 ID <br> 로그인할 때 만들고 회전해도 유지함 |
| token_hash | CHAR(64) | NOT NULL, UNIQUE | refresh 토큰의 SHA-256 해시(16진수) <br> 토큰 원문은 저장하지 않음 |
| expires_at | TIMESTAMPTZ | NOT NULL | 발급 뒤 14일 <br> `family_expires_at`을 넘지 않음 |
| family_expires_at | TIMESTAMPTZ | NOT NULL | 로그인 뒤 30일 <br> 회전해도 바뀌지 않음 |
| replaced_at | TIMESTAMPTZ | NULL 허용 | 회전으로 새 토큰에 자리를 넘긴 시각 |
| revoked_at | TIMESTAMPTZ | NULL 허용 | 로그아웃, 비밀번호 변경, 재사용 감지로 폐기한 시각 |
| created_at | TIMESTAMPTZ | NOT NULL, 기본 now() | |

**인덱스**

| 인덱스 | 쓰는 곳 |
|---|---|
| `token_hash` 유니크 | refresh 요청에서 토큰 찾기 |
| `(user_id)` | 사용자의 모든 토큰 폐기 |
| `(family_id)` | 로그인 묶음 폐기 |
| `(expires_at)` | 오래된 기록 정리 |

refresh 토큰은 32바이트 무작위 값을 Base64URL로 인코딩한 문자열이다. 무작위 값이라 느린 해시가 필요 없고, SHA-256 해시로 바로 찾는다. DB가 유출되어도 해시로는 토큰을 쓸 수 없다.

<br>

### 3-2-4. 상태 변화

| 사건 | users | refresh_tokens |
|---|---|---|
| 가입 | 행 추가, `role = PENDING` | |
| 처음 관리자 생성 | 행 추가, `role = ADMIN`, `must_change_password = true`, `temp_password_expires_at = NULL` | |
| 승인 | `role = MEMBER`, `approved_at` 기록 | |
| 거절 | 행 삭제 | CASCADE로 함께 삭제 |
| 로그인 | 임시 비밀번호였으면 `temp_password_used_at` 기록 | 새 로그인 묶음의 첫 행 추가 |
| refresh | | 쓴 행에 `replaced_at` 기록, 같은 묶음에 새 행 추가 |
| 로그아웃 | | 그 묶음의 모든 행에 `revoked_at` 기록 |
| 역할 변경 | `role` 변경 | |
| 비밀번호 변경 | `password_hash` 교체, `must_change_password = false`, 임시 비밀번호 컬럼 비움, `tokens_valid_after = now()` | 그 사용자의 모든 행 폐기 후 새 로그인 묶음 추가 |
| 임시 비밀번호 발급 | `password_hash`를 임시 비밀번호 해시로 교체, `must_change_password = true`, `temp_password_expires_at = now() + 24시간`, `temp_password_used_at = NULL`, `tokens_valid_after = now()` | 그 사용자의 모든 행 폐기 |
| 탈퇴 | `email`, `name`, `password_hash`를 NULL로, `withdrawn_at` 기록, `tokens_valid_after = now()` | 그 사용자의 모든 행 삭제 |

<br>

### 3-2-5. 다른 기능의 참조

- 글, 첨부파일, 앨범의 작성자 컬럼은 `users.id`를 외래 키로 참조한다. 삭제 규칙은 기본값(`NO ACTION`)으로 둔다. 활성 계정과 탈퇴한 계정의 행은 지우지 않으므로 참조가 깨지지 않는다.
- 행을 지우는 경우는 승인 대기 계정의 거절뿐이다. 승인 대기 회원은 글과 앨범을 만들 수 없어서 참조하는 행이 없다.
- 탈퇴한 작성자를 "탈퇴한 회원"으로 보여 주는 방법은 3-1에서 정한다. ([3-1-DESIGN-SOFTWARE-ARCHITECTURE.md](3-1-DESIGN-SOFTWARE-ARCHITECTURE.md))

<br>

### 3-2-6. 데이터 정리

- 매일 한 번 `expires_at`이나 `revoked_at`이 하루보다 오래된 `refresh_tokens` 행을 지운다.
- `users`는 자동으로 지우지 않는다. 보존 기간은 요구사항 문서를 따른다. ([2-REQUIREMENTS.md](2-REQUIREMENTS.md))
