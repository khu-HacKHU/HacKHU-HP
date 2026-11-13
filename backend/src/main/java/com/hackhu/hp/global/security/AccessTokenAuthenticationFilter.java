package com.hackhu.hp.global.security;

import com.hackhu.hp.global.auth.AccessTokenAuthenticator;
import com.hackhu.hp.global.auth.AccessTokenAuthenticator.AuthenticatedSession;
import com.hackhu.hp.global.auth.LoginUserAuthentication;
import com.hackhu.hp.global.error.CommonErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 access 토큰을 검증해 현재 사용자를 SecurityContext에 넣는다 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md
 * 5-1-4, 3-1-1).
 *
 * <ul>
 *   <li>토큰은 {@code Authorization: Bearer} 헤더에서만 꺼낸다. 쿼리스트링, 본문, 쿠키는 보지 않는다.
 *   <li>헤더가 없으면 방문자로 다룬다. 아무것도 넣지 않고 넘기면 경로 규칙이 공개 API만 통과시킨다.
 *   <li>헤더가 있는데 받아 줄 수 없는 토큰이면 공개 API여도 {@code 401 UNAUTHORIZED}로 끝낸다. 이유와 상관없이 같은 응답이다 (A14).
 *       프론트엔드는 이 401을 받으면 refresh를 한 번 시도한다 (A13).
 *   <li>{@code /api/v1/auth/**}(가입, 로그인, refresh, 로그아웃)는 access 토큰을 쓰지 않으므로 헤더를 보지 않는다. 만료된 토큰이 실린
 *       로그인 요청이 401로 막히지 않게 하려는 것이다. 프론트엔드는 이 API들의 401에 refresh를 시도하지 않는다 (4-2-1).
 *   <li>비밀번호 변경 필요 상태면 내 정보 조회와 비밀번호 변경만 통과시키고 나머지는 {@code 403 AUTH_PASSWORD_CHANGE_REQUIRED}로 끝낸다.
 *       refresh와 로그아웃은 위 규칙으로 이미 통과한다 (5-1-3).
 * </ul>
 */
@Slf4j
final class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private static final RequestMatcher AUTH_API =
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/auth/**");

    private static final RequestMatcher ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED =
            new OrRequestMatcher(
                    PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.GET, "/api/v1/users/me"),
                    PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.PUT, "/api/v1/users/me/password"));

    private final AccessTokenAuthenticator authenticator;
    private final SecurityErrorResponseWriter errorWriter;
    private final SecurityContextHolderStrategy contextHolder =
            SecurityContextHolder.getContextHolderStrategy();

    AccessTokenAuthenticationFilter(
            AccessTokenAuthenticator authenticator, SecurityErrorResponseWriter errorWriter) {
        this.authenticator = authenticator;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || AUTH_API.matches(request)) {
            chain.doFilter(request, response);
            return;
        }

        Optional<AuthenticatedSession> session =
                bearerToken(header).flatMap(authenticator::authenticate);
        if (session.isEmpty()) {
            errorWriter.write(response, CommonErrorCode.UNAUTHORIZED);
            return;
        }

        AuthenticatedSession authenticated = session.get();
        if (authenticated.mustChangePassword()
                && !ALLOWED_WHILE_PASSWORD_CHANGE_REQUIRED.matches(request)) {
            log.info(
                    "비밀번호 변경 필요 상태라 거부: userId={}, method={}, path={}",
                    authenticated.user().id(),
                    ApiAccessDeniedHandler.forLog(request.getMethod()),
                    ApiAccessDeniedHandler.forLog(request.getRequestURI()));
            errorWriter.write(response, SecurityErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED);
            return;
        }

        SecurityContext context = contextHolder.createEmptyContext();
        context.setAuthentication(new LoginUserAuthentication(authenticated.user()));
        contextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    // 인증 방식 이름은 대소문자를 가리지 않는다 (RFC 7235 2.1). Bearer가 아니거나 토큰이 비었으면 받지 않는다.
    private static Optional<String> bearerToken(String header) {
        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).strip();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
