package com.hackhu.hp.global.security;

import com.hackhu.hp.global.error.ErrorCode;
import com.hackhu.hp.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Security 필터에서 공통 에러 응답을 쓴다.
 *
 * <p>필터에서 거부된 요청은 컨트롤러에 닿지 않아 {@code GlobalExceptionHandler}가 처리하지 못한다. 그래서 같은 {@link
 * ErrorResponse} 형식을 여기서 직접 쓴다.
 */
final class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code));
    }
}
