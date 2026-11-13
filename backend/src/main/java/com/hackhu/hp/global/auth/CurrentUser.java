package com.hackhu.hp.global.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 컨트롤러 파라미터에 현재 로그인 사용자({@link LoginUser})를 넣는다.
 *
 * <pre>{@code
 * @PostMapping("/api/v1/notices")
 * ResponseEntity<NoticeResponse> create(@CurrentUser LoginUser user, @Valid @RequestBody NoticeCreateRequest request)
 * }</pre>
 *
 * <ul>
 *   <li>로그인이 필요한 API(기본값)에서는 null이 아니다. 비로그인 요청은 컨트롤러에 오기 전에 401로 끝난다.
 *   <li>공개 API에서 비로그인 요청이면 null이다. 공개 API에서는 null을 방문자로 다룬다.
 * </ul>
 *
 * <p>사용법은 docs/spec/auth/backend/3-1-DESIGN-SOFTWARE-ARCHITECTURE.md에 있다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal
public @interface CurrentUser {}
