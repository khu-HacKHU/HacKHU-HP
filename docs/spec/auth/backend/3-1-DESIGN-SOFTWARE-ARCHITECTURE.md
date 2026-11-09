# 3-1 소프트웨어 아키텍처

<!-- 정하는 것: 구성 요소, 패키지 구조, 의존 방향, 도메인 모델. 공통 패키지 규칙은 레포의 backend/AGENTS.md를 따른다 -->

본 문서는 인증권한 백엔드의 구성 요소와 의존 방향을 정한다.

지금은 다른 기능이 현재 사용자와 역할을 확인하는 방법(3-1-1), 회원과 가입(3-1-2), 로그인과 토큰(3-1-3), 요청 횟수 제한(3-1-4)을 정했다. 가입 승인의 구성 요소는 구현 이슈(#45)에서 채운다.

<br>

### 3-1-1. 현재 사용자와 역할 확인

게시판, 갤러리 같은 다른 기능이 "지금 요청한 사람이 누구이고 어떤 역할인가"를 알아내는 방법이다. 권한 규칙 자체는 보안 문서가 정한다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md))

**구성 요소**

| 구성 요소 | 위치 | 하는 일 |
|---|---|---|
| `Role` | `global/auth` | 역할 enum <br> `PENDING`, `MEMBER`, `STAFF`, `ADMIN` 순으로 높음 |
| `LoginUser` | `global/auth` | 현재 사용자 <br> `id`(사용자 ID)와 `role`만 담음 |
| `@CurrentUser` | `global/auth` | 컨트롤러 파라미터에 `LoginUser`를 넣음 |
| `LoginUserAuthentication` | `global/auth` | SecurityContext에 들어가는 인증 객체 <br> access 토큰 검증 필터만 만듦 |
| `SecurityConfig` | `global/security` | 경로별 최소 역할과 역할 계층 |

**요청이 지나가는 순서**

1. access 토큰 검증 필터가 토큰의 `sid`로 로그인 세션과 그 계정을 DB에서 함께 읽는다. 세션이 폐기되거나 만료되었거나 계정이 탈퇴했으면 401로 끝난다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md) 5-1-4)
2. DB에서 읽은 역할로 `LoginUser`를 만들고 `LoginUserAuthentication`에 담아 SecurityContext에 넣는다. 토큰이 없으면 아무것도 넣지 않는다. 비밀번호 변경 필요 상태의 403도 이 필터가 처리한다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md) 5-1-3)
3. `SecurityConfig`의 경로 규칙이 최소 역할을 확인한다. 비로그인은 401, 역할 부족은 403으로 여기서 끝난다.
4. `@PreAuthorize`가 붙은 메서드는 그 역할을 한 번 더 확인한다.
5. 컨트롤러가 `@CurrentUser LoginUser user`로 현재 사용자를 받아 서비스에 넘긴다.

역할을 토큰이 아니라 DB에서 읽으므로 역할 변경과 탈퇴가 다음 요청부터 반영된다. ([DECISIONS.md](DECISIONS.md) A2)

access 토큰 검증 필터(1, 2단계)의 구성은 3-1-3에 있다. 다른 기능의 컨트롤러 테스트(`@WebMvcTest`)에는 이 필터가 없으므로 아래 테스트 도우미로 사용자를 넣는다.

**컨트롤러에서 받기**

```java
@PostMapping("/api/v1/notices")
ResponseEntity<NoticeResponse> create(
        @CurrentUser LoginUser user, @Valid @RequestBody NoticeCreateRequest request) {
    NoticeResponse response = noticeService.create(user, request);
    return ResponseEntity.created(URI.create("/api/v1/notices/" + response.id())).body(response);
}
```

- 로그인이 필요한 API(기본값)에서 `user`는 null이 아니다. 비로그인 요청은 컨트롤러에 오기 전에 401로 끝난다.
- 공개 API에서 비로그인 요청이면 `user`는 null이다. null을 방문자로 다룬다.
- 작성자 ID는 요청 본문이 아니라 `user.id()`에서 꺼낸다. 요청 DTO에 `authorId`, `role`을 두지 않는다. (CWE-915)

**서비스에 넘기기**

서비스 메서드는 `LoginUser`를 인자로 받는다. 서비스에서 `SecurityContextHolder`를 직접 읽지 않는다. 인자로 받으면 서비스 테스트에서 사용자를 바로 넣을 수 있고, 어떤 메서드가 현재 사용자에 따라 동작이 달라지는지 시그니처에 드러난다.

```java
public NoticeResponse create(LoginUser user, NoticeCreateRequest request) {
    Notice notice = Notice.create(user.id(), request.title(), request.content());
    ...
}
```

**역할 검사**

| 검사 | 쓰는 곳 | 방법 |
|---|---|---|
| 경로마다 정해진 최소 역할 | `SecurityConfig` | 기본은 `MEMBER` 이상 <br> 공개 API와 다른 역할이 필요한 경로는 PL 리뷰를 받아 추가함 |
| API 하나가 정해진 역할을 요구 | 컨트롤러나 서비스 메서드 | `@PreAuthorize("hasRole('STAFF')")` |
| 작성자 확인처럼 조건에 따라 다른 검사 | 서비스 | `user.id()`, `user.hasRoleAtLeast(Role.STAFF)`, `user.isAdmin()` |

- 역할 순서를 따른다. `hasRole('MEMBER')`와 `hasRoleAtLeast(Role.MEMBER)`는 운영진과 관리자도 통과한다.
- 작성자 확인(IDOR 방지)은 각 기능이 직접 구현한다. 공통 헬퍼를 두지 않는다. ([제품 DECISIONS.md](../../DECISIONS.md) D7, CWE-639)

```java
// 작성자이거나 관리자만 고칠 수 있다
if (notice.getAuthorId() != user.id() && !user.isAdmin()) {
    throw new BusinessException(NoticeErrorCode.NOTICE_FORBIDDEN);
}
```

`user.id()`는 `long`이라서 엔티티의 `Long authorId`와 `!=`, `==`로 비교해도 값으로 비교된다. 단, `authorId`가 `null`이면 언박싱할 때 예외가 나므로 nullable한 값은 `Objects.equals(user.id(), authorId)`로 비교한다. 두 쪽이 모두 `Long`이면 `==`가 참조를 비교하므로 `Objects.equals`를 쓴다.

**승인 대기 회원**

승인 대기 회원은 내 정보 API를 빼면 방문자와 같다. ([PRODUCT.md](../../PRODUCT.md) 4절)

- 다른 기능의 API에 `hasRole('PENDING')`, `isAuthenticated()`, `authenticated()`를 쓰지 않는다. 승인 대기 회원이 통과한다.
- 공개 API에서 `user != null`을 "동아리원"으로 해석하지 않는다. `user != null && user.hasRoleAtLeast(Role.MEMBER)`로 본다.

```java
// 공개 API에서 동아리원 여부로 응답을 나누는 경우
boolean member = user != null && user.hasRoleAtLeast(Role.MEMBER);
```

**테스트**

| 도우미 | 쓰는 곳 |
|---|---|
| `LoginUserRequests.loginUser(id, role)` | MockMvc 요청 하나 <br> `mockMvc.perform(get(...).with(loginUser(7L, Role.MEMBER)))` |
| `@WithLoginUser(id = 7, role = Role.MEMBER)` | 테스트 메서드나 클래스 전체 <br> `@PreAuthorize`가 붙은 서비스를 직접 부르는 테스트에도 적용됨 |

- 둘 다 `src/test/java`의 `com.hackhu.hp.global.auth`에 있다. 예시는 `CurrentUserTest`.
- `with(user("1").roles("MEMBER"))`, `@WithMockUser`로 만든 사용자는 `LoginUser`가 아니라서 `@CurrentUser`가 null이 된다. `@CurrentUser`를 쓰는 API는 위 도우미로 테스트한다.
- 권한이 걸린 API는 비로그인 401, 승인 대기 회원 403, 역할 부족이나 남의 리소스 403(또는 404)을 테스트한다. ([backend/AGENTS.md](../../../../backend/AGENTS.md))

<br>

### 3-1-2. 회원과 가입

회원(`users` 테이블)과 가입 API의 구성 요소다. 테이블은 스키마 문서가, 요청과 응답은 계약 문서가 정한다. ([3-2-DESIGN-DB-SCHEMA.md](3-2-DESIGN-DB-SCHEMA.md), [4-2-INTERACTION-CONTRACT.md](4-2-INTERACTION-CONTRACT.md))

**구성 요소**

| 구성 요소 | 위치 | 하는 일 |
|---|---|---|
| `User` | `domain/user/entity` | 회원 엔티티 <br> 가입은 `User.signUp(email, name, passwordHash)`로만 만들고 역할은 `PENDING`으로 시작함 |
| `Emails` | `domain/user/entity` | 이메일 정규화 <br> 앞뒤 공백을 지우고 ASCII일 때만 소문자로 바꿈 <br> 로그인도 같은 함수를 씀 |
| `UserRepository` | `domain/user/repository` | `users` 조회와 저장 |
| `SignupRequest`, `SignupResponse` | `domain/user/dto` | 가입 요청과 응답 <br> 요청은 생성자에서 정규화한 뒤 검증함 |
| `@Password`, `@PersonName`, `@PasswordNotEmail` | `domain/user/dto/validation` | 비밀번호 규칙, 이름 규칙, 비밀번호와 이메일 비교 <br> 같은 규칙을 쓰는 다른 API도 이 애너테이션을 붙임 |
| `SignupService` | `domain/user/service` | 중복 확인, 비밀번호 해시, 저장 |
| `SignupController` | `domain/user/controller` | `POST /api/v1/auth/signup` |
| `UserErrorCode` | `domain/user/exception` | 회원 에러 코드 ([5-3-QUALITY-ERROR-HANDLING.md](5-3-QUALITY-ERROR-HANDLING.md)) |
| `PasswordEncoderConfig` | `global/security` | bcrypt cost 12의 `PasswordEncoder` 빈 |

- 가입 API의 경로는 `/api/v1/auth` 아래지만 회원을 만드는 일이라 `domain/user`에 둔다. 로그인과 토큰은 `domain/auth`에 둔다. ([backend/AGENTS.md](../../../../backend/AGENTS.md))
- `Role` enum은 3-1-1의 `global/auth`에 있다. `User.role`도 이 enum을 쓴다.
- 다른 기능은 `users.id`만 참조하고 `User` 엔티티를 직접 읽지 않는다. 작성자 이름이 필요하면 인증권한팀에 공개 메서드를 요청한다.

**가입 처리 순서**

1. `SignupRequest` 생성자가 이메일과 이름을 정규화한다. 비밀번호는 바꾸지 않는다.
2. Bean Validation이 필드 규칙을 검사한다. 실패하면 400과 칸별 사유를 주고 끝난다.
3. `SignupService`가 활성 계정이 그 이메일을 쓰는지 확인한다. 쓰면 bcrypt를 계산하지 않고 409로 끝난다.
4. 비밀번호를 bcrypt로 해시하고 `User.signUp`으로 행을 저장한다.
5. 3과 4 사이에 같은 이메일이 먼저 저장되었으면 `uk_users_email` 위반이 나고, 이를 409로 바꾼다. 다른 제약 위반은 바꾸지 않고 500으로 둔다. ([5-2-QUALITY-CONCURRENCY.md](5-2-QUALITY-CONCURRENCY.md))
6. `201`, `Location: /api/v1/users/me`와 가입한 회원을 준다. 토큰은 주지 않는다.

`SignupService.signup`은 트랜잭션으로 묶지 않는다. bcrypt 계산(약 0.3초) 동안 DB 연결을 잡지 않으려는 것이다. 쓰기는 행 추가 하나라 따로 묶을 것이 없다. ([DECISIONS.md](DECISIONS.md) A15)

**시각 컬럼**

- `created_at`은 DB의 `now()`가 정하고, 저장한 뒤 엔티티가 그 값을 다시 읽는다(`@Generated`). 서버 시계와 DB 시계가 섞이지 않게 하려는 것이다.
- `updated_at`은 행이 바뀔 때마다 트리거 `set_updated_at()`이 갱신한다. 조건부 `UPDATE` 쿼리로 바꿔도 따로 적지 않아도 된다. ([DECISIONS.md](DECISIONS.md) A19)

**요청 횟수 제한**

`SignupController`가 서비스를 부르기 전에 IP 기준 제한(1시간에 50회)을 확인한다. 한도를 넘으면 bcrypt를 계산하지 않고 `429`와 `Retry-After`로 끝난다. 본문 검증(400)을 통과한 요청만 센다. 검증에서 끝나는 요청은 bcrypt를 계산하지 않기 때문이다. 카운터와 클라이언트 IP 판별은 3-1-4를 따른다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md) 5-1-7)

<br>

### 3-1-3. 로그인과 토큰

로그인, refresh, 로그아웃 API와 요청마다 하는 인증의 구성 요소다. 요청과 응답은 계약 문서가, 회전 규칙은 4-2-5가, 잠금과 쿼리는 동시성 문서가 정한다. ([4-2-INTERACTION-CONTRACT.md](4-2-INTERACTION-CONTRACT.md), [5-2-QUALITY-CONCURRENCY.md](5-2-QUALITY-CONCURRENCY.md))

**구성 요소**

| 구성 요소 | 위치 | 하는 일 |
|---|---|---|
| `AccessTokenService` | `global/auth` | access 토큰(JWT) 발급과 서명, 발급자, 만료 검증 <br> `HS256` 고정, 서명 키가 없거나 32바이트보다 짧으면 서버가 시작하지 않음 |
| `AccessTokenAuthenticator` | `global/auth` | 토큰으로 현재 사용자를 정하는 인터페이스 <br> 필터가 도메인 패키지에 기대지 않게 인터페이스만 둠 |
| `LoginSessionAuthenticator` | `domain/auth/service` | 위 인터페이스의 구현 <br> `sid`의 로그인 세션과 계정을 한 번에 읽어 폐기, 만료, 사용자 불일치, 탈퇴를 거부함 |
| `AccessTokenAuthenticationFilter` | `global/security` | `Authorization: Bearer`에서 토큰을 꺼내 위 구현을 부르고 `LoginUserAuthentication`을 SecurityContext에 넣음 <br> 비밀번호 변경 필요 상태의 403도 여기서 줌 |
| `AuthOriginFilter` | `global/security` | 로그인, refresh, 로그아웃의 `Origin` 허용 목록 검사 <br> 허용 목록은 CORS와 같은 `app.cors.allowed-origins` |
| `AuthController` | `domain/auth/controller` | `POST /api/v1/auth/login`, `/refresh`, `/logout` |
| `LoginService` | `domain/auth/service` | 요청 횟수 제한, bcrypt 검증, 일반과 임시 비밀번호 경로, 같은 브라우저의 기존 세션 폐기 |
| `TokenRefreshService` | `domain/auth/service` | 세션 행을 잠그고 4-2-5의 회전 규칙을 적용함 |
| `LogoutService` | `domain/auth/service` | refresh 쿠키의 세션 하나를 폐기함 |
| `SessionIssuer` | `domain/auth/service` | 로그인 세션, refresh 토큰, access 토큰을 만듦 <br> 부르는 쪽의 트랜잭션 안에서만 돎 |
| `LoginFailureMonitor` | `domain/auth/service` | 한 계정에 1시간 50회 넘는 로그인 실패를 경고 로그로 남김 |
| `LoginSessionCleanup` | `domain/auth/service` | 매일 04:30(UTC)에 끝난 지 하루가 지난 세션을 지움 (3-2-7) |
| `LoginSession`, `RefreshToken` | `domain/auth/entity` | `login_sessions`, `refresh_tokens` 테이블 |
| `AuthErrorCode` | `domain/auth/exception` | `AUTH_INVALID_CREDENTIALS` ([5-3-QUALITY-ERROR-HANDLING.md](5-3-QUALITY-ERROR-HANDLING.md)) |

- `global/security`의 필터가 `domain/auth`를 직접 부르지 않는다. `SecurityConfig`는 `AccessTokenAuthenticator` 빈이 있을 때만 필터를 붙인다. 다른 기능의 `@WebMvcTest`에는 이 빈이 없어 필터 없이 테스트 도우미(3-1-1)로 사용자를 넣는다.
- 서버 시각은 `Clock` 빈(`global/config/ClockConfig`, UTC)에서 읽는다. 토큰 만료, 세션 수명, 회전 시각, 요청 제한 창이 모두 이 시계를 쓴다. 임시 비밀번호 만료(`temp_password_expires_at`)는 5-2-3의 쿼리대로 DB의 `now()`와 비교한다.

**로그인 처리 순서**

1. `AuthOriginFilter`가 `Origin`을 확인한다. 다르면 403으로 끝난다.
2. 본문이 JSON이 아니면 415, 비었으면 400으로 끝난다. 이메일은 가입과 같은 `Emails.normalize`로 정규화한다.
3. `LoginService`가 IP 기준 제한(모든 시도)을 세고, IP와 이메일 조합 제한(실패만)에서 한 번을 잡는다. 한도면 429로 끝난다. (3-1-4)
4. 활성 계정을 이메일로 찾고 bcrypt로 비밀번호를 검증한다. 계정이 없으면 미리 만든 더미 해시와 비교한다. 72바이트가 넘는 비밀번호는 저장될 수 없으므로 틀린 것으로 보고 계산은 그대로 한다.
5. 한 트랜잭션에서 5-2-3대로 해시를 다시 확인한다. 임시 비밀번호가 있는 계정은 조건부 갱신, 아니면 `users` 행 잠금이다. 통과하면 요청에 실려 온 refresh 쿠키의 세션을 폐기하고 새 세션을 만든다.
6. 실패면 401 `AUTH_INVALID_CREDENTIALS`이고 잡아 둔 한 번은 실패로 남는다. 성공이면 잡아 둔 한 번을 돌려준다.

**요청마다 하는 인증**

| 요청 | 처리 |
|---|---|
| `Authorization` 헤더 없음 | 방문자. 아무것도 넣지 않음 |
| `/api/v1/auth/**` | 헤더를 보지 않음 ([DECISIONS.md](DECISIONS.md) A22) |
| `Bearer`가 아니거나, 토큰이 비었거나, 받아 줄 수 없는 토큰 | 공개 API여도 401 `UNAUTHORIZED` <br> 이유는 DEBUG 로그에만 남김 |
| 비밀번호 변경 필요 상태 | 내 정보 조회, 비밀번호 변경만 통과 <br> 나머지는 403 `AUTH_PASSWORD_CHANGE_REQUIRED` |
| 그 밖 | `LoginUserAuthentication`을 넣고 경로 규칙(3-1-1 3단계)으로 넘김 |

<br>

### 3-1-4. 요청 횟수 제한

로그인, 가입, 비밀번호 변경과 탈퇴의 요청 횟수 제한을 하는 공통 구성 요소다. 규칙의 값은 보안 문서가 정한다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md) 5-1-7)

| 구성 요소 | 위치 | 하는 일 |
|---|---|---|
| `RateLimiter` | `global/security` | 고정 창 카운터 <br> Caffeine 캐시, 항목 수 상한 10만 개, 창이 끝나면 지워짐 <br> 키마다 원자적으로 늘리고 확인함 |
| `AuthRateLimits` | `global/security` | 5-1-7의 규칙 네 개 |
| `ClientIpResolver` | `global/security` | 믿는 프록시에서 온 연결만 `X-Forwarded-For`의 오른쪽 값을 씀 (CWE-348) <br> 설정은 `app.client-ip.trusted-proxies`, `trusted-hops` |
| `TooManyRequestsException` | `global/error` | `429 TOO_MANY_REQUESTS`와 `Retry-After`(초) |

| 규칙 | 붙인 곳 | 세는 것 |
|---|---|---|
| `LOGIN_ATTEMPTS_PER_IP` | `LoginService` | 모든 시도 |
| `LOGIN_FAILURES_PER_IP_AND_EMAIL` | `LoginService` | 실패 <br> 확인 전에 `tryAcquire`로 잡고 성공하면 `release`로 돌려줌 |
| `SIGNUP_ATTEMPTS_PER_IP` | `SignupController` | 본문 검증을 통과한 모든 시도 |
| `PASSWORD_CONFIRM_FAILURES_PER_USER` | 비밀번호 변경, 탈퇴(#49) | 지금 비밀번호가 틀린 시도 <br> 로그인과 같이 확인 전에 잡고 맞으면 돌려줌 |

- 실패만 세는 규칙을 시도한 뒤에 세면 동시에 들어온 시도가 모두 확인을 통과해 한도를 넘는다. 그래서 확인 전에 한 번을 잡는다. ([DECISIONS.md](DECISIONS.md) A21, 5-2-5)
- 거부 로그는 창마다 처음 거부할 때만 남긴다. 거부할 때마다 남기면 누구나 로그를 쌓을 수 있다. (CWE-779)
- 카운터는 서버 메모리에 있다. 재배포하면 초기화되고 인스턴스를 늘리면 느슨해진다. ([DECISIONS.md](DECISIONS.md) A11)
