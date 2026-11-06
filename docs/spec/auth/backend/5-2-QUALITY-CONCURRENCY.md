# 5-2 동시성

본 문서는 인증권한 작업이 동시에 일어날 때의 처리, 잠금과 그 순서를 정한다. 어떤 작업을 누가 할 수 있는지는 보안 문서가 정한다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))

<br>

### 5-2-1. 원칙

- 확인한 상태를 근거로 쓰는 작업은 확인과 쓰기를 한 트랜잭션 안에서 원자적으로 한다. 확인하고 나서 쓰기 전에 다른 요청이 상태를 바꾸면, 이미 틀린 확인을 믿고 쓰게 되기 때문이다. (CWE-362)
- 원자적으로 하는 방법은 둘 중 하나다.
  - 조건부 갱신: 확인할 조건을 `UPDATE`나 `DELETE`의 `WHERE`에 넣고, 바뀐 행 수로 성공을 판단한다.
  - 행 잠금: `SELECT ... FOR UPDATE`로 행을 잠근 뒤 확인하고 쓴다.
- 처음 읽은 상태로 처리 경로를 고르는 작업(예: 대상이 관리자인지에 따라 잠금을 잡을지 정함)은, 그 경로가 전제한 상태를 갱신의 `WHERE`에 넣는다. 그 사이 상태가 바뀌면 0행이 되고, 다시 읽어 맞는 경로로 처리한다. 경로를 고른 뒤 상태가 바뀌어 잘못된 경로로 끝나는 것을 막기 위해서다.
- 비밀번호 해시 검증(bcrypt, 약 0.3초)은 잠금 밖에서 한다. 잠금을 오래 잡지 않기 위해서다. 대신 검증할 때 읽은 해시(`:verifiedHash`)를 기억해 두고, 조건부 갱신이나 잠금 안에서 그 해시가 아직 현재 값인지 확인한다.
- 계정을 바꾸는 작업(비밀번호 변경, 비밀번호 초기화, 탈퇴)은 `users` 행을 먼저 갱신한 뒤 세션을 폐기한다. 로그인은 `users` 행을 잠근 뒤 세션을 만든다. 두 작업이 같은 `users` 행에서 차례로 처리되므로, 어느 순서로 끝나도 옛 비밀번호로 만든 세션이 남지 않는다.
- 격리 수준은 PostgreSQL 기본값(READ COMMITTED)을 쓴다. 조건부 갱신은 잠긴 행을 기다린 뒤 바뀐 행으로 `WHERE`를 다시 확인한다.
- 잠금은 `users`, `login_sessions`, `refresh_tokens` 순서로 잡는다. `users` 행 여러 개를 잠글 때는 `id` 오름차순으로 잡는다. 순서를 지켜 교착을 막는다.

<br>

### 5-2-2. 작업별 처리

| 작업 | 겹칠 수 있는 작업 | 처리 |
|---|---|---|
| 가입 | 같은 이메일로 동시 가입 | `uk_users_email` 위반을 409로 바꿈 |
| 일반 로그인 | 비밀번호 변경, 비밀번호 초기화, 탈퇴 | 비밀번호를 검증한 뒤 5-2-3의 잠금 안에서 해시를 다시 확인하고 세션을 만듦 |
| 임시 비밀번호 로그인 | 같은 임시 비밀번호로 동시 로그인, 재발급, 탈퇴 | 5-2-3의 조건부 갱신이 1행일 때만 세션을 만듦 |
| refresh | 같은 세션의 동시 refresh, 로그아웃, 세션 폐기 | `login_sessions` 행을 `FOR UPDATE`로 잠그고 폐기와 만료를 확인한 뒤 회전 규칙을 적용함 (4-2-INTERACTION-CONTRACT.md 4-2-5) |
| 로그아웃 | refresh | `login_sessions` 행 갱신이 같은 행 잠금을 거침 |
| 비밀번호 변경 | 다른 기기의 비밀번호 변경, 비밀번호 초기화, 로그인 | 5-2-3의 조건부 갱신이 1행일 때만 세션을 폐기하고 새 세션을 만듦 <br> 0행이면 400 |
| 임시 비밀번호 발급 | 대상의 역할 변경, 탈퇴, 로그인 | 5-2-3의 조건부 갱신이 1행일 때만 대상의 세션을 폐기함 <br> 0행이면 다시 읽어 404나 403 |
| 가입 승인 | 거절, 대상의 탈퇴 | `UPDATE users SET role = 'MEMBER', approved_at = now() WHERE id = :id AND role = 'PENDING' AND withdrawn_at IS NULL` <br> 0행이면 다시 읽어 404나 409 |
| 가입 거절 | 승인, 대상의 탈퇴 | `DELETE FROM users WHERE id = :id AND role = 'PENDING' AND withdrawn_at IS NULL` <br> 0행이면 다시 읽어 404나 409 |
| 역할 변경 | 다른 관리자의 역할 변경, 관리자 탈퇴, 임시 비밀번호 발급 | 5-2-4 |
| 탈퇴 | 로그인, refresh, 비밀번호 변경, 역할 변경(승격 포함) | 5-2-3의 조건부 갱신이 1행일 때만 세션을 지움 <br> 처음 읽은 역할로 고른 경로의 역할 조건을 갱신에 넣음 <br> 관리자 경로는 먼저 5-2-4의 잠금을 잡음 |
| 처음 관리자 생성 | 서버 여러 대가 동시에 시작 | 트랜잭션 수준 advisory lock(`pg_advisory_xact_lock`, 상수 키) 안에서 활성 관리자 수를 확인하고 만듦 |

<br>

### 5-2-3. 비밀번호를 확인하는 작업

**일반 로그인**

비밀번호를 검증한 뒤 한 트랜잭션에서 아래를 한다.

```sql
SELECT password_hash
  FROM users
 WHERE id = :id
   AND withdrawn_at IS NULL
   FOR UPDATE
```

- 읽은 `password_hash`가 `:verifiedHash`와 같을 때만 세션을 만든다. 행이 없거나 다르면 401이다.
- 검증 직후 비밀번호가 바뀌었다면, 바꾼 쪽이 이미 커밋했으면 해시가 달라 401이 되고, 아직 커밋하지 않았으면 이 잠금이 기다린 뒤 바뀐 해시를 읽어 401이 된다. 로그인이 먼저 커밋했으면 바꾼 쪽이 그 세션까지 폐기한다.

**임시 비밀번호 로그인**

`temp_password_expires_at`이 NULL이 아닌 계정의 로그인은 항상 이 경로로 처리한다. 일반 로그인 경로로 보내지 않는다. 일반 경로로 보내면 이미 쓴 임시 비밀번호로 다시 로그인할 수 있기 때문이다. 처음 관리자 계정은 `temp_password_expires_at`이 NULL이라 일반 로그인 경로를 쓴다.

비밀번호를 검증한 뒤 아래 갱신을 실행하고, 바뀐 행이 1개일 때만 같은 트랜잭션에서 세션을 만든다. 0개면 401이다.

```sql
UPDATE users
   SET temp_password_used_at = now()
 WHERE id = :id
   AND password_hash = :verifiedHash
   AND withdrawn_at IS NULL
   AND temp_password_used_at IS NULL
   AND temp_password_expires_at > now()
```

같은 임시 비밀번호로 두 요청이 동시에 와도 한 요청만 성공한다. 검증 직후 운영진이 새 임시 비밀번호를 발급하면 해시가 달라 0행이 되므로, 옛 임시 비밀번호로 세션이 생기거나 새 임시 비밀번호가 사용 처리되지 않는다.

**비밀번호 변경**

지금 비밀번호를 검증한 뒤 아래 갱신을 실행한다. 바뀐 행이 1개일 때만 같은 트랜잭션에서 그 사용자의 살아 있는 세션을 모두 폐기하고 이 기기의 새 세션을 만든다.

```sql
UPDATE users
   SET password_hash = :newHash,
       must_change_password = false,
       temp_password_expires_at = NULL,
       temp_password_used_at = NULL,
       updated_at = now()
 WHERE id = :id
   AND password_hash = :verifiedHash
   AND withdrawn_at IS NULL
```

0행이면 그 사이 다른 곳에서 비밀번호가 바뀐 것이다. 지금 비밀번호가 틀린 경우와 같은 400을 준다.

**임시 비밀번호 발급**

권한 검사를 통과한 뒤 아래 갱신을 실행한다. 바뀐 행이 1개일 때만 같은 트랜잭션에서 대상의 살아 있는 세션을 모두 폐기한다. `:allowedRoles`는 요청한 사람이 운영진이면 `PENDING`, `MEMBER`이고 관리자면 모든 역할이다. (5-1-QUALITY-SECURITY.md 5-1-3)

```sql
UPDATE users
   SET password_hash = :tempHash,
       must_change_password = true,
       temp_password_expires_at = now() + interval '24 hours',
       temp_password_used_at = NULL,
       updated_at = now()
 WHERE id = :targetId
   AND id <> :actorId
   AND withdrawn_at IS NULL
   AND role IN (:allowedRoles)
```

권한 검사와 갱신 사이에 대상이 승격되면 0행이 되어 비밀번호가 바뀌지 않는다.

**탈퇴**

비밀번호를 검증한 뒤, 처음 읽은 역할에 따라 두 경로 중 하나로 처리한다. 어느 경로든 아래 갱신이 1행일 때만 같은 트랜잭션에서 그 사용자의 세션을 모두 지운다.

```sql
UPDATE users
   SET email = NULL,
       name = NULL,
       password_hash = NULL,
       withdrawn_at = now(),
       updated_at = now()
 WHERE id = :id
   AND password_hash = :verifiedHash
   AND withdrawn_at IS NULL
   AND role <> 'ADMIN'   -- 관리자 경로에서는 AND role = 'ADMIN'
```

| 처음 읽은 역할 | 처리 | 0행일 때 |
|---|---|---|
| 관리자가 아님 | 관리자 잠금 없이 `role <> 'ADMIN'` 조건으로 갱신함 | 다시 읽음 <br> 그 사이 관리자가 되었으면 관리자 경로로 다시 처리함 <br> 비밀번호가 바뀌었거나 이미 탈퇴했으면 400 |
| 관리자 | 5-2-4의 잠금을 잡고 활성 관리자를 센 뒤 `role = 'ADMIN'` 조건으로 갱신함 <br> 대상이 마지막 활성 관리자면 409 | 다시 읽음 <br> 그 사이 관리자가 아니게 되었으면 비관리자 경로로 다시 처리함 <br> 비밀번호가 바뀌었거나 이미 탈퇴했으면 400 |

- 관리자 경로의 잠금은 활성 관리자 행을 모두 잠그므로, 그동안 다른 관리자의 강등이나 탈퇴는 기다린다. 비관리자 경로에서 승격된 것을 0행으로 알아채고 관리자 경로로 넘어가면, 승격 뒤의 관리자 수로 다시 센다.
- 경로를 바꿔 다시 처리하는 것은 한 번만 한다. 다시 처리해도 0행이면 409를 주고 사용자가 다시 시도하게 한다. 역할이 계속 바뀌는 동안 무한히 반복하지 않기 위해서다.
- 예: 관리자가 A 한 명이고 `MEMBER`인 X가 탈퇴하는 도중 A가 X를 `ADMIN`으로 올리고 자기 역할을 내리면, X의 비관리자 경로 갱신은 `role <> 'ADMIN'`에 걸려 0행이 된다. 관리자 경로로 다시 처리하면 활성 관리자는 X 한 명이라 409가 되고, 관리자가 0명이 되지 않는다.

<br>

### 5-2-4. 마지막 활성 관리자

관리자 두 명이 동시에 서로의 역할을 내리거나, 한 명은 탈퇴하고 한 명은 자기 역할을 내리면 둘 다 "관리자가 한 명 더 있다"는 확인을 통과해 관리자가 0명이 될 수 있다. 관리자가 아니던 사람이 역할 변경이나 탈퇴 도중 관리자로 승격되는 경우도 같다.

- 활성 관리자의 역할을 내리거나 활성 관리자가 탈퇴하는 작업은 먼저 아래 잠금을 잡고 센다. 잠금을 잡은 뒤 대상이 아직 활성 관리자인지도 다시 확인한다.

```sql
SELECT id
  FROM users
 WHERE role = 'ADMIN'
   AND withdrawn_at IS NULL
 ORDER BY id
   FOR UPDATE
```

- 센 수가 1이고 대상이 그 한 명이면 409를 준다. 아니면 같은 트랜잭션에서 대상을 바꾼다. 역할 변경은 `UPDATE users SET role = :newRole WHERE id = :id AND role = 'ADMIN' AND withdrawn_at IS NULL`, 탈퇴는 5-2-3의 관리자 경로 갱신(`role = 'ADMIN'`)이다. 0행이면 다시 읽어, 그 사이 대상이 관리자가 아니게 되었으면 아래 비관리자 경로로 한 번 다시 처리한다.
- 관리자가 아닌 사람의 탈퇴는 5-2-3의 비관리자 경로로 처리한다. 갱신 조건에 `role <> 'ADMIN'`을 넣어, 그 사이 관리자가 되었으면 이 잠금 경로로 다시 처리한다.
- 대상이 관리자가 아닌 역할 변경(예: `MEMBER`를 `STAFF`로, `STAFF`를 `ADMIN`으로)은 이 잠금 없이 `UPDATE users SET role = :newRole WHERE id = :id AND role IN ('MEMBER', 'STAFF') AND withdrawn_at IS NULL`로 한다. 조건에 `ADMIN`을 넣지 않으므로, 그 사이 대상이 관리자가 되었으면 0행이 되어 마지막 관리자 확인을 건너뛰지 않는다. 0행이면 다시 읽어 상태에 맞게 처리한다. 대상이 이제 관리자면 위의 잠금 경로로 다시 처리하고, 없거나 탈퇴했으면 404, 승인 대기면 409를 준다. 경로를 바꿔 다시 처리하는 것은 탈퇴와 같이 한 번만 하고, 또 0행이면 409를 준다.

<br>

### 5-2-5. 받아들이는 경합

- 요청마다 하는 인증(5-1-4)은 잠금 없이 읽는다. 로그아웃이나 강등과 같은 순간에 이미 검사를 통과한 요청 하나는 이전 상태로 끝날 수 있다. 그다음 요청부터는 반영된다.
- 요청한 사람의 역할은 요청을 시작할 때 읽은 값을 쓴다. 처리 도중 그 사람이 강등되어도 그 요청은 이전 역할로 끝난다. 대상 쪽 조건은 위의 조건부 갱신으로 확인하므로 대상의 상태가 바뀐 경우는 막힌다.
- 요청 횟수 제한 카운터는 서버 메모리에서 원자적으로 늘린다. 동시 요청이 와도 한도를 넘겨 통과시키지 않는다.
