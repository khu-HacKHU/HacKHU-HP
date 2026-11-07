# 3-1 소프트웨어 아키텍처

<!-- 정하는 것: 구성 요소, 패키지 구조, 의존 방향, 도메인 모델. 공통 패키지 규칙은 레포의 backend/AGENTS.md를 따른다 -->

본 문서는 인증권한 백엔드의 구성 요소와 의존 방향을 정한다.

지금은 다른 기능이 현재 사용자와 역할을 확인하는 방법(3-1-1)과 회원과 가입(3-1-2)을 정했다. 토큰 발급, 가입 승인의 구성 요소는 구현 이슈(#25, #45)에서 채운다.

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

> access 토큰 검증 필터(1, 2단계)는 로그인 API(#25)에서 만든다. 그 전에는 실제 요청에 로그인 사용자가 실리지 않는다. 다른 기능은 아래 테스트 도우미로 개발하고 테스트한다.

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

`user.id()`는 `long`이라서 엔티티의 `Long authorId`와 `!=`, `==`로 비교해도 값으로 비교된다. `Long`끼리 `==`로 비교하면 128 이상에서 값이 같아도 다르다고 나오므로, 두 쪽이 모두 `Long`이면 `equals`를 쓴다.

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

가입의 IP 기준 제한(1시간에 50회)은 아직 없다. 로그인 API(#25)에서 클라이언트 IP 판별과 제한 카운터를 만들 때 가입에도 붙인다. ([5-1-QUALITY-SECURITY.md](5-1-QUALITY-SECURITY.md) 5-1-7)
