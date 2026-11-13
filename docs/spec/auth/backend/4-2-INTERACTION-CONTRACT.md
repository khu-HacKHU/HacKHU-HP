# 4-2 API 계약

본 문서는 인증권한 API의 엔드포인트, 요청과 성공 응답 예시, 필수와 선택 필드를 정한다. 공통 형식은 API 규약을 따른다. ([api.md](../../../conventions/api.md)) 실패 응답의 에러 코드 목록은 5-3에서 정하고, 여기서는 프론트엔드가 분기해야 하는 코드만 적는다.

<br>

### 4-2-1. 공통

**access 토큰**

- 로그인이 필요한 API는 `Authorization: Bearer {accessToken}` 헤더로 access 토큰을 받는다.
- access 토큰을 쿼리스트링, 요청 본문, 쿠키로 받지 않는다.
- 프론트엔드는 access 토큰을 메모리에만 둔다. `localStorage`와 `sessionStorage`에 두지 않는다. 보관 방법은 프론트엔드 세트에서 정한다. ([4-2-QUALITY.md](../frontend/4-2-QUALITY.md))

**refresh 토큰 쿠키**

```http
Set-Cookie: refresh_token={값}; Path=/api/v1/auth; Max-Age=1209600; HttpOnly; Secure; SameSite=Strict
```

- 쿠키는 `/api/v1/auth` 아래 경로에만 실린다. 다른 API 요청에는 실리지 않는다.
- `Max-Age`는 14일과 로그인 세션의 남은 수명 중 짧은 쪽이다.
- 쿠키를 지울 때는 같은 이름과 경로로 `Max-Age=0`을 보낸다.
- 쿠키를 설정하거나 지우는 API는 로그인, refresh, 로그아웃, 비밀번호 변경, 탈퇴다.

**credentials**

프론트엔드와 API의 주소가 다르면(예: `hackhu.example`과 `api.hackhu.example`) 같은 사이트여도 출처(origin)가 다르다. 이때 브라우저는 `credentials: 'include'`가 없는 요청의 `Set-Cookie`를 저장하지 않고 쿠키도 보내지 않는다. 그래서 아래처럼 정한다.

- 프론트엔드는 모든 API 요청에 `credentials: 'include'`를 붙인다. 쿠키를 다루는 API만 골라 붙이면 빠뜨리기 쉽기 때문이다. 쿠키는 `Path=/api/v1/auth`라서 다른 API에는 붙여도 실리지 않는다.
- 백엔드는 모든 API 응답에 허용한 프론트엔드 주소 하나를 `Access-Control-Allow-Origin`으로, `Access-Control-Allow-Credentials: true`를 함께 준다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))
- 출처가 다르면 브라우저는 기본 응답 헤더 몇 개만 JavaScript에 보여 준다. 프론트엔드가 읽어야 하는 `Retry-After`와 `Location`은 `Access-Control-Expose-Headers: Retry-After, Location`으로 열어 준다.

**프론트엔드가 분기하는 실패**

| 상태 | 코드 | 뜻 | 프론트엔드 동작 |
|---|---|---|---|
| 401 | `UNAUTHORIZED` | 토큰이 없거나, 만료되었거나, 서명이 맞지 않음, 또는 로그아웃, 비밀번호 변경, 탈퇴로 로그인 세션이 폐기됨 | refresh를 한 번 시도하고 원래 요청을 한 번 다시 보냄 <br> refresh도 401이면 로그인 화면으로 보냄 |
| 403 | `AUTH_PASSWORD_CHANGE_REQUIRED` | 비밀번호 변경 필요 상태에서 허용되지 않은 API를 부름 | 비밀번호 변경 화면으로 보냄 |
| 403 | `FORBIDDEN` | 역할이 부족함 | 권한 없음 안내 |
| 429 | `TOO_MANY_REQUESTS` | 요청 횟수 제한에 걸림 | `Retry-After` 헤더의 초만큼 기다리라고 안내 |

**401을 받았을 때 프론트엔드 흐름**

- 만료와 그 밖의 401을 응답 코드로 나누지 않는다. 프론트엔드는 어차피 같은 동작을 하고, 나누어 주면 탈취한 토큰을 던져 본 공격자가 "서명은 맞고 시간만 지난 진짜 토큰"인지 알 수 있다. 만료 여부는 서버 로그에만 남긴다.
- 프론트엔드는 access 토큰을 보낸 요청이 401을 받으면 refresh를 한 번 시도한다. 서명 키를 교체해 기존 access 토큰이 401을 받아도 refresh 토큰은 그대로라서 로그인이 이어진다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))
- 원래 요청을 다시 보내기 전에, refresh 응답의 `user.id`가 원래 요청을 만든 사용자의 ID와 같은지 확인한다. 다르면 원래 요청을 다시 보내지 않고 버린다. refresh 쿠키는 브라우저의 모든 탭이 함께 쓰므로, 다른 탭에서 다른 계정으로 로그인했다면 그 계정의 토큰을 받기 때문이다. 그대로 다시 보내면 사용자 A가 쓴 글이 사용자 B 명의로 저장되거나 A의 화면에 B의 데이터가 보인다. (CWE-287) 쓰기와 조회 요청 모두에 적용한다.
- 원래 요청을 버렸으면 탭의 현재 사용자를 refresh 응답의 사용자로 바꾸고 화면을 다시 그린다. 쓰던 입력은 지우지 않고 "다른 탭에서 계정이 바뀌어 요청을 보내지 않았다"고 안내한다.
- 원래 요청은 한 번만 다시 보낸다. 다시 보낸 요청이 또 401이면 refresh를 다시 부르지 않고 로그인 화면으로 보낸다.
- refresh, 로그인, 로그아웃 API 자체의 401에는 refresh를 시도하지 않는다.
- 이 확인은 서버가 대신할 수 없다. 갱신에 쓰인 refresh 토큰과 새로 발급한 access 토큰이 모두 B의 정상 토큰이기 때문이다.
- refresh 요청은 한 번에 하나만 보내고, 응답을 받거나 네트워크 오류로 끝날 때까지 기다린다. 시간 초과로 요청을 중간에 끊고(`AbortController`) 다시 보내지 않는다. 끊은 요청의 응답이 늦게 도착해 쿠키가 뒤바뀌는 것을 막기 위해서다.

**요청 형식**

- 본문이 있는 요청은 `Content-Type: application/json`만 받는다. 다른 형식이면 415를 준다. 다른 사이트의 HTML 폼은 JSON을 보낼 수 없으므로, 폼으로 로그인 요청을 위조하는 것을 막는다.
- `/api/v1/admin` 아래 API는 운영진 이상이 쓰는 API를 묶은 것이다. 관리자만 쓰는 API도 있으므로, 경로가 아니라 API마다 정한 인증 수준으로 권한을 판단한다. (5-1-QUALITY-SECURITY.md 5-1-2)

**캐시**

로그인, refresh, 비밀번호 변경, 임시 비밀번호 발급 응답에는 `Cache-Control: no-store`를 붙인다.

<br>

### 4-2-2. 목록

인증 수준과 권한 검사는 보안 문서가 기준이다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))

| 기능 | 메서드와 경로 | 인증 수준 | 성공 |
|---|---|---|---|
| 가입 | `POST /api/v1/auth/signup` | 공개 | 201 |
| 로그인 | `POST /api/v1/auth/login` | 공개 | 200 |
| access 토큰 갱신 | `POST /api/v1/auth/refresh` | refresh 쿠키 | 200 |
| 로그아웃 | `POST /api/v1/auth/logout` | refresh 쿠키 | 204 |
| 내 정보 조회 | `GET /api/v1/users/me` | 로그인 | 200 |
| 비밀번호 변경 | `PUT /api/v1/users/me/password` | 로그인 | 200 |
| 탈퇴 | `DELETE /api/v1/users/me` | 로그인 | 204 |
| 회원 목록 | `GET /api/v1/admin/users` | 운영진 | 200 |
| 가입 승인 | `POST /api/v1/admin/users/{userId}/approve` | 운영진 | 200 |
| 가입 거절 | `POST /api/v1/admin/users/{userId}/reject` | 운영진 | 204 |
| 역할 변경 | `PUT /api/v1/admin/users/{userId}/role` | 관리자 | 200 |
| 임시 비밀번호 발급 | `POST /api/v1/admin/users/{userId}/temporary-password` | 운영진 | 200 |

<br>

### 4-2-3. 가입

`POST /api/v1/auth/signup`. 공개.

```json
{
  "email": "newbie@example.com",
  "password": "(비밀번호)",
  "name": "홍길동"
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| email | 필수 | 이메일 형식, 254자 이하 <br> 서버가 앞뒤 공백을 지우고 소문자로 바꿈 |
| password | 필수 | 비밀번호 규칙을 따름 ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md)) |
| name | 필수 | 앞뒤 공백을 지운 뒤 1자 이상 30자 이하 |

성공하면 `201`과 `Location: /api/v1/users/me`를 준다. 가입만 하고 로그인시키지 않는다. 토큰을 주지 않는다.

```json
{
  "id": 17,
  "email": "newbie@example.com",
  "name": "홍길동",
  "role": "PENDING",
  "createdAt": "2026-11-05T09:30:00Z"
}
```

| 실패 | 상태 |
|---|---|
| 필수 항목이 비었거나 규칙에 맞지 않음 | 400, 필드별 사유를 `errors`에 담음 |
| 활성 계정이 이미 쓰는 이메일 | 409 |

<br>

### 4-2-4. 로그인

`POST /api/v1/auth/login`. 공개.

```json
{
  "email": "member@example.com",
  "password": "(비밀번호)"
}
```

성공하면 본문으로 access 토큰을, `Set-Cookie`로 refresh 토큰을 준다.

```json
{
  "accessToken": "(access 토큰)",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 17,
    "email": "member@example.com",
    "name": "홍길동",
    "role": "MEMBER",
    "mustChangePassword": false
  }
}
```

- `expiresIn`은 access 토큰이 만료될 때까지 남은 초다.
- 승인 대기 회원도 로그인된다. `role`이 `PENDING`이면 프론트엔드가 승인 대기 안내를 보여 준다.
- `mustChangePassword`가 `true`면 프론트엔드는 비밀번호 변경 화면으로 보낸다.
- 로그인에 성공했고 요청에 기존 refresh 쿠키가 실려 왔으면(같은 브라우저에서 다시 로그인) 그 쿠키의 로그인 세션을 폐기한 뒤 새 세션을 만든다. 로그인에 실패하면 기존 세션을 건드리지 않는다. 쿠키를 덮어써서 주인 없는 세션이 30일 동안 남는 것을 막는다.
- `Origin` 헤더가 허용 목록(프론트엔드 주소)에 있어야 한다. 다른 사이트가 피해자의 브라우저를 공격자 계정으로 로그인시키는 것(로그인 CSRF)을 막는다.

| 실패 | 상태 |
|---|---|
| 이메일이나 비밀번호가 비었음 | 400 |
| 이메일이 없거나, 비밀번호가 틀리거나, 탈퇴한 계정이거나, 임시 비밀번호가 만료되었거나 이미 쓰였거나, 검증 직후 비밀번호가 바뀜 | 401 `AUTH_INVALID_CREDENTIALS`, 모두 같은 코드와 메시지 |
| `Origin` 헤더가 허용 목록에 없음 | 403 |
| `Content-Type`이 JSON이 아님 | 415 |
| 요청 횟수 제한 | 429 |

<br>

### 4-2-5. access 토큰 갱신

`POST /api/v1/auth/refresh`. 본문 없음. refresh 토큰 쿠키로 인증한다.

성공하면 새 access 토큰을 본문으로, 회전한 새 refresh 토큰을 `Set-Cookie`로 준다. 본문은 로그인 응답과 같다. 새로고침 뒤 프론트엔드가 이 응답만으로 로그인 상태를 되살릴 수 있게 `user`를 함께 준다. 프론트엔드는 이 `user.id`로 원래 요청을 다시 보내도 되는지 확인한다. (4-2-1)

**회전 규칙**

서버는 받은 토큰이 로그인 세션의 현재 토큰인지, 현재 토큰의 바로 앞 토큰인지를 보고 처리한다. 현재 토큰은 세션에서 아직 회전하지도 폐기하지도 않은 토큰 하나다. (3-2-DESIGN-DB-SCHEMA.md) 같은 세션의 refresh 요청은 세션 행을 잠가 하나씩 처리한다.

| 받은 토큰 | 판단 | 처리 |
|---|---|---|
| 현재 토큰 | 정상 갱신 | 새 토큰으로 회전하고 `Set-Cookie`로 줌 |
| 회전한 지 10초 이내인 토큰 <br> 몇 단계 앞이든 상관없음 | 동시 요청 <br> 다른 탭이 먼저 회전했거나, 한 탭이 짧은 시간에 연속으로 회전함 | 새 access 토큰만 주고 `Set-Cookie`는 보내지 않음 <br> 먼저 처리된 응답의 쿠키가 브라우저에 남음 |
| 바로 앞 토큰이고 회전한 지 10초가 지남 | 응답 유실 <br> 현재 토큰이 한 번도 쓰이지 않았으므로 클라이언트가 받지 못한 것으로 봄 | 현재 토큰을 폐기하고, 받은 토큰의 다음 토큰을 새로 발급해 `Set-Cookie`로 줌 <br> 받은 토큰의 `replaced_by_id`를 새 토큰으로, `replaced_at`을 지금으로 바꿈 |
| 그 밖의 예전 토큰, 응답 유실 복구로 폐기된 토큰 | 재사용 <br> 탈취로 봄 | 로그인 세션을 폐기하고 401을 줌 <br> 그 세션의 access 토큰도 다음 요청부터 거부됨 |

위에서부터 차례로 보고 처음 맞는 줄로 처리한다.

응답이 유실되어도 클라이언트는 다음 갱신에서 쿠키를 되찾는다. 10초 안에 다시 시도하면 access 토큰만 받고 이전 쿠키가 남지만, 그 뒤의 갱신은 세 번째 줄에 해당해 새 쿠키를 받는다. 예전 토큰의 기록은 세션이 끝날 때까지 남으므로, 하루 이상 지나서 다시 시도해도 같다. (3-2-DESIGN-DB-SCHEMA.md 3-2-7)

요청이 도착한 순서와 처리 순서가 달라도 정상 사용자는 끊기지 않는다. 예를 들어 탭 A가 T0로 T1을 받고 곧바로 T1로 T2를 받은 뒤 탭 B가 보낸 T0 요청이 늦게 처리되면, T0는 두 단계 앞이지만 회전한 지 10초 안이라 두 번째 줄에 해당한다.

탈취한 토큰을 정상 사용자보다 먼저 쓰면 공격자가 새 쿠키를 받는다. 이때 정상 사용자의 토큰은 폐기되므로, 정상 사용자가 다음에 갱신할 때 네 번째 줄에 해당해 세션 전체가 끊긴다.

| 실패 | 상태 |
|---|---|
| 쿠키가 없거나, 모르는 토큰이거나, 만료된 토큰, 폐기된 세션, 재사용된 토큰 | 401 `UNAUTHORIZED`, 쿠키를 지움 |
| 계정이 탈퇴했거나 거절되어 없음 | 401 `UNAUTHORIZED`, 쿠키를 지움 |
| `Origin` 헤더가 허용 목록에 없음 | 403 |

<br>

### 4-2-6. 로그아웃

`POST /api/v1/auth/logout`. 본문 없음. refresh 토큰 쿠키로 대상을 정한다.

이 요청의 로그인 세션만 폐기하고 `204`와 쿠키 삭제를 준다. 그 세션에서 발급한 access 토큰은 만료 전이라도 다음 요청부터 `401 UNAUTHORIZED`를 받는다. 같은 브라우저의 다른 탭도 같은 세션이므로 함께 로그아웃된다. 다른 기기의 로그인은 유지된다. 쿠키가 없거나 이미 폐기된 세션이어도 `204`를 준다. `Origin` 헤더가 허용 목록에 없으면 세션을 건드리지 않고 403을 준다.

<br>

### 4-2-7. 내 정보 조회

`GET /api/v1/users/me`. 로그인. 승인 대기 회원과 비밀번호 변경 필요 상태도 부를 수 있다.

```json
{
  "id": 17,
  "email": "member@example.com",
  "name": "홍길동",
  "role": "MEMBER",
  "mustChangePassword": false,
  "createdAt": "2026-11-05T09:30:00Z"
}
```

<br>

### 4-2-8. 비밀번호 변경

`PUT /api/v1/users/me/password`. 로그인. 승인 대기 회원과 비밀번호 변경 필요 상태도 부를 수 있다.

```json
{
  "currentPassword": "(지금 비밀번호)",
  "newPassword": "(새 비밀번호)"
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| currentPassword | 필수 | 지금 비밀번호 <br> 임시 비밀번호로 로그인했으면 임시 비밀번호 |
| newPassword | 필수 | 비밀번호 규칙을 따름 <br> `currentPassword`와 같으면 안 됨 |

성공하면 이 기기를 포함한 모든 로그인 세션을 폐기하고, 이 기기에는 새 로그인 세션을 만들어 준다. 변경 전에 발급한 access 토큰은 발급 시각과 상관없이 모두 거부된다. 본문은 로그인 응답과 같고 `Set-Cookie`로 새 refresh 토큰을 준다. `user.mustChangePassword`는 `false`가 된다.

| 실패 | 상태 |
|---|---|
| 규칙에 맞지 않음 | 400 |
| `currentPassword`가 틀리거나, 검증 직후 다른 곳에서 비밀번호가 바뀜 | 400 <br> 401을 쓰지 않음 (프론트엔드가 토큰 문제로 오해하지 않게) |
| 요청 횟수 제한 | 429 |

<br>

### 4-2-9. 탈퇴

`DELETE /api/v1/users/me`. 로그인. 승인 대기 회원도 부를 수 있다.

```json
{
  "password": "(지금 비밀번호)"
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| password | 필수 | 지금 비밀번호 <br> 탈퇴는 되돌릴 수 없으므로 access 토큰만으로 하지 않고 비밀번호를 다시 확인함 |

`DELETE` 요청에 본문을 싣는다. 프론트엔드는 `fetch`의 `body`로 보내고, 백엔드는 `@RequestBody`로 받는다.

성공하면 계정을 익명화하고 모든 토큰을 폐기한 뒤 `204`와 쿠키 삭제를 준다.

| 실패 | 상태 |
|---|---|
| `password`가 비었거나 틀림, 또는 검증 직후 비밀번호가 바뀜 | 400 <br> 401을 쓰지 않음 |
| 활성 관리자가 자기 한 명뿐임 | 409 |
| 처리 도중 자기 역할이 계속 바뀌어 처리하지 못함 | 409 <br> 다시 시도하면 됨 (5-2-QUALITY-CONCURRENCY.md 5-2-3) |
| 요청 횟수 제한 | 429 |

<br>

### 4-2-10. 회원 목록

`GET /api/v1/admin/users?role=PENDING&page=0&size=20&sort=createdAt,desc`. 운영진.

| 쿼리 | 필수 | 규칙 |
|---|---|---|
| role | 선택 | `PENDING`, `MEMBER`, `STAFF`, `ADMIN` 중 하나 <br> 없으면 모든 역할 |
| page, size | 선택 | API 규약을 따름 |
| sort | 선택 | `createdAt`만 허용 <br> 기본 `createdAt,desc` |

탈퇴한 계정은 목록에 나오지 않는다.

```json
{
  "content": [
    {
      "id": 17,
      "email": "newbie@example.com",
      "name": "홍길동",
      "role": "PENDING",
      "createdAt": "2026-11-05T09:30:00Z",
      "approvedAt": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

<br>

### 4-2-11. 가입 승인

`POST /api/v1/admin/users/{userId}/approve`. 운영진. 본문 없음.

성공하면 역할이 `MEMBER`가 되고 회원 목록의 항목 하나와 같은 형식으로 그 회원을 준다.

| 실패 | 상태 |
|---|---|
| 회원이 없거나 탈퇴함 | 404 |
| 승인 대기 회원이 아님 | 409 |

<br>

### 4-2-12. 가입 거절

`POST /api/v1/admin/users/{userId}/reject`. 운영진. 본문 없음.

성공하면 계정을 지우고 `204`를 준다. 같은 이메일로 다시 가입할 수 있다.

| 실패 | 상태 |
|---|---|
| 회원이 없거나 탈퇴함 | 404 |
| 승인 대기 회원이 아님 | 409 |

<br>

### 4-2-13. 역할 변경

`PUT /api/v1/admin/users/{userId}/role`. 관리자.

```json
{
  "role": "STAFF"
}
```

| 필드 | 필수 | 규칙 |
|---|---|---|
| role | 필수 | `MEMBER`, `STAFF`, `ADMIN` 중 하나 <br> `PENDING`으로 바꿀 수 없음 |

성공하면 회원 목록의 항목 하나와 같은 형식으로 그 회원을 준다. 지금과 같은 역할로 바꾸면 아무것도 바꾸지 않고 성공을 준다.

| 실패 | 상태 |
|---|---|
| `role`이 허용 값이 아님 | 400 |
| 회원이 없거나 탈퇴함 | 404 |
| 대상이 승인 대기 회원임 | 409 <br> 승인은 가입 승인 API로만 함 |
| 활성 관리자가 대상 한 명뿐인데 역할을 내리려 함 | 409 |
| 처리 도중 대상의 역할이 계속 바뀌어 처리하지 못함 | 409 <br> 다시 시도하면 됨 (5-2-QUALITY-CONCURRENCY.md 5-2-4) |

<br>

### 4-2-14. 임시 비밀번호 발급

`POST /api/v1/admin/users/{userId}/temporary-password`. 운영진. 본문 없음.

성공하면 대상의 비밀번호를 임시 비밀번호로 바꾸고 대상의 모든 로그인을 끊는다. 임시 비밀번호 원문은 이 응답에서 한 번만 준다. 서버는 해시만 저장하므로 다시 볼 수 없다.

```json
{
  "temporaryPassword": "(임시 비밀번호 16자)",
  "expiresAt": "2026-11-06T09:30:00Z"
}
```

| 실패 | 상태 |
|---|---|
| 회원이 없거나 탈퇴함 | 404 |
| 운영진이 자기와 같거나 높은 역할인 사람을 대상으로 함 | 403 |
| 자기 자신을 대상으로 함 | 403 <br> 자기 비밀번호는 비밀번호 변경 API로 바꿈 |
