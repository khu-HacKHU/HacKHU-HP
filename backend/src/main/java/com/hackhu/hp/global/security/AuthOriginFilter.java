package com.hackhu.hp.global.security;

import com.hackhu.hp.global.error.CommonErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 로그인, refresh, 로그아웃은 {@code Origin} 헤더가 허용 목록(프론트엔드 주소)에 있을 때만 받는다
 * (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-5, CWE-352).
 *
 * <p>이 세 API는 access 토큰 대신 refresh 쿠키를 쓰거나 새로 준다. Security 설정에서 CSRF 토큰을 끈 것을 이 검사와 {@code
 * SameSite=Strict} 쿠키가 보완한다. 빠지면 다른 사이트가 피해자의 브라우저로 refresh를 불러 토큰을 회전시키고, 피해자는 재사용으로 판정되어 로그아웃된다.
 * 로그인은 다른 사이트가 피해자를 공격자 계정으로 로그인시키는 것(로그인 CSRF)을 막는다.
 *
 * <p>{@code Origin}이 없거나 다르면 컨트롤러에 닿기 전에 {@code 403 FORBIDDEN}으로 끝낸다. 세션과 쿠키를 건드리지 않는다. CORS 필터는
 * {@code Origin}이 없는 요청과 같은 출처의 요청을 그냥 통과시키므로 이 검사를 대신하지 못한다.
 *
 * <p>누구나 일으킬 수 있는 거부라 로그를 남기지 않는다 (CWE-779).
 */
final class AuthOriginFilter extends OncePerRequestFilter {

    private static final RequestMatcher COOKIE_APIS =
            new OrRequestMatcher(
                    PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.POST, "/api/v1/auth/login"),
                    PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.POST, "/api/v1/auth/refresh"),
                    PathPatternRequestMatcher.withDefaults()
                            .matcher(HttpMethod.POST, "/api/v1/auth/logout"));

    private final List<String> allowedOrigins;
    private final SecurityErrorResponseWriter errorWriter;

    AuthOriginFilter(List<String> allowedOrigins, SecurityErrorResponseWriter errorWriter) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (COOKIE_APIS.matches(request) && !isAllowed(request)) {
            errorWriter.write(response, CommonErrorCode.FORBIDDEN);
            return;
        }
        chain.doFilter(request, response);
    }

    // 정확히 같은 문자열만 받는다. 헤더가 여러 개면 하나라도 다르면 거부한다.
    private boolean isAllowed(HttpServletRequest request) {
        List<String> origins = Collections.list(request.getHeaders(HttpHeaders.ORIGIN));
        return !origins.isEmpty() && origins.stream().allMatch(allowedOrigins::contains);
    }
}
