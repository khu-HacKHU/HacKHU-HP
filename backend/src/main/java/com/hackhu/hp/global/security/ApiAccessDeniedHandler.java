package com.hackhu.hp.global.security;

import com.hackhu.hp.global.error.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 로그인은 했지만 역할이 부족하면 {@code 403 FORBIDDEN}을 준다.
 *
 * <p>권한 거부는 사용자 ID와 요청 경로를 로그로 남긴다 (5-1-10). principal 이름은 사용자 ID여야 한다. 이메일이나 이름을 넣지 않는다 (5-1-9).
 */
@Slf4j
final class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private static final int MAX_LOG_VALUE_LENGTH = 200;

    private final SecurityErrorResponseWriter errorWriter;

    ApiAccessDeniedHandler(SecurityErrorResponseWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication == null ? null : authentication.getName();
        log.info(
                "권한 거부: userId={}, method={}, path={}",
                userId,
                forLog(request.getMethod()),
                forLog(request.getRequestURI()));
        errorWriter.write(response, CommonErrorCode.FORBIDDEN);
    }

    // 메서드와 경로는 클라이언트가 정하는 값이다. 줄바꿈과 제어 문자를 바꿔 로그 줄을 위조하지 못하게 하고,
    // 긴 경로로 로그를 부풀리지 못하게 자른다 (CWE-117).
    static String forLog(String value) {
        if (value == null) {
            return null;
        }
        String oneLine = value.replaceAll("[\r\n]", "_");
        String printable = oneLine.replaceAll("\\p{Cntrl}", "_");
        return printable.length() > MAX_LOG_VALUE_LENGTH
                ? printable.substring(0, MAX_LOG_VALUE_LENGTH)
                : printable;
    }
}
