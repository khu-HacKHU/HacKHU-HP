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
- `Max-Age`는 14일과 로그인 묶음의 남은 수명 중 짧은 쪽이다.
- 쿠키를 지울 때는 같은 이름과 경로로 `Max-Age=0`을 보낸다.
- 프론트엔드는 refresh와 로그아웃 요청에 `credentials: 'include'`를 붙인다.

**프론트엔드가 분기하는 실패**

| 상태 | 코드 | 뜻 | 프론트엔드 동작 |
|---|---|---|---|
| 401 | `TOKEN_EXPIRED` | access 토큰이 만료됨 | refresh API를 부르고 원래 요청을 한 번 다시 보냄 |
| 401 | `UNAUTHORIZED` | 토큰이 없거나 위조됨, 또는 비밀번호 변경이나 탈퇴로 무효가 됨 | 로그인 화면으로 보냄 |
| 403 | `AUTH_PASSWORD_CHANGE_REQUIRED` | 비밀번호 변경 필요 상태에서 허용되지 않은 API를 부름 | 비밀번호 변경 화면으로 보냄 |
| 403 | `FORBIDDEN` | 역할이 부족함 | 권한 없음 안내 |
| 429 | `TOO_MANY_REQUESTS` | 요청 횟수 제한에 걸림 | `Retry-After` 헤더의 초만큼 기다리라고 안내 |

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

| 실패 | 상태 |
|---|---|
| 이메일이나 비밀번호가 비었음 | 400 |
| 이메일이 없거나, 비밀번호가 틀리거나, 탈퇴한 계정이거나, 임시 비밀번호가 만료되었거나 이미 쓰였음 | 401, 모두 같은 코드와 메시지 |
| 요청 횟수 제한 | 429 |

<br>

### 4-2-5. access 토큰 갱신

`POST /api/v1/auth/refresh`. 본문 없음. refresh 토큰 쿠키로 인증한다.

성공하면 새 access 토큰을 본문으로, 회전한 새 refresh 토큰을 `Set-Cookie`로 준다. 본문은 로그인 응답과 같다. 새로고침 뒤 프론트엔드가 이 응답만으로 로그인 상태를 되살릴 수 있게 `user`를 함께 준다.

- 회전한 지 10초 안에 직전 토큰이 다시 오면 새 access 토큰만 주고 `Set-Cookie`는 보내지 않는다. 여러 탭이 동시에 갱신하는 경우다.
- 회전한 지 10초가 지난 직전 토큰이 다시 오면 탈취로 보고 그 로그인 묶음 전체를 폐기한 뒤 401을 준다.

| 실패 | 상태 |
|---|---|
| 쿠키가 없거나, 모르는 토큰이거나, 만료, 폐기, 재사용된 토큰 | 401 `UNAUTHORIZED`, 쿠키를 지움 |
| 계정이 탈퇴했거나 거절되어 없음 | 401 `UNAUTHORIZED`, 쿠키를 지움 |
| `Origin` 헤더가 허용 목록에 없음 | 403 |

<br>

### 4-2-6. 로그아웃

`POST /api/v1/auth/logout`. 본문 없음. refresh 토큰 쿠키로 대상을 정한다.

이 요청의 로그인 묶음만 폐기하고 `204`와 쿠키 삭제를 준다. 다른 기기의 로그인은 유지된다. 쿠키가 없거나 이미 폐기된 토큰이어도 `204`를 준다. access 토큰은 프론트엔드가 메모리에서 지운다.

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

성공하면 모든 기기의 로그인을 끊고, 이 기기에는 새 로그인 묶음을 만들어 준다. 본문은 로그인 응답과 같고 `Set-Cookie`로 새 refresh 토큰을 준다. `user.mustChangePassword`는 `false`가 된다.

| 실패 | 상태 |
|---|---|
| 규칙에 맞지 않음 | 400 |
| `currentPassword`가 틀림 | 400 <br> 401을 쓰지 않음 (프론트엔드가 토큰 문제로 오해하지 않게) |
| 요청 횟수 제한 | 429 |

<br>

### 4-2-9. 탈퇴

`DELETE /api/v1/users/me`. 로그인. 승인 대기 회원도 부를 수 있다. 본문 없음.

성공하면 계정을 익명화하고 모든 토큰을 폐기한 뒤 `204`와 쿠키 삭제를 준다.

| 실패 | 상태 |
|---|---|
| 활성 관리자가 자기 한 명뿐임 | 409 |

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
