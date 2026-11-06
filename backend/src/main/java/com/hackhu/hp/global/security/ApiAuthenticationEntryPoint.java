package com.hackhu.hp.global.security;

import com.hackhu.hp.global.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 로그인이 필요한 API를 로그인 없이 부르면 {@code 401 UNAUTHORIZED}를 준다.
 *
 * <p>누구나 부를 수 있는 경로라 로그를 남기지 않는다. 남기면 로그를 마음대로 쌓을 수 있게 된다 (CWE-779). 만료된 토큰의 {@code TOKEN_EXPIRED}는
 * 토큰 검증을 붙일 때 여기서 나눈다 (5-1-4).
 */
final class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter errorWriter;

    ApiAuthenticationEntryPoint(SecurityErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        errorWriter.write(response, CommonErrorCode.UNAUTHORIZED);
    }
}
