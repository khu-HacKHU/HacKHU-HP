# 3-1 소프트웨어 아키텍처

<!-- 정하는 것: 구성 요소, 패키지 구조, 의존 방향, 도메인 모델. 공통 패키지 규칙은 레포의 backend/AGENTS.md를 따른다 -->

본 문서는 인증권한 백엔드의 구성 요소와 의존 방향을 정한다.

지금은 다른 기능이 현재 사용자와 역할을 확인하는 방법(3-1-1)만 정했다. 회원, 토큰 발급, 가입 승인의 구성 요소는 구현 이슈(#24, #25, #45)에서 채운다.

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
