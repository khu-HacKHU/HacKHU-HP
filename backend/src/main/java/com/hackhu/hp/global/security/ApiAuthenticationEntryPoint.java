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
 * <p>토큰이 없거나, 만료됐거나, 무효인 경우 모두 같은 응답을 준다. 만료를 따로 알려 주지 않는다 (결정 기록 A14, 5-1-4).
 *
 * <p>누구나 부를 수 있는 경로라 여기서는 로그를 남기지 않는다. 남기면 로그를 마음대로 쌓을 수 있게 된다 (CWE-779).
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
