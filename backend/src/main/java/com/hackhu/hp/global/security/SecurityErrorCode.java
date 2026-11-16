package com.hackhu.hp.global.security;

import com.hackhu.hp.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Security 필터가 직접 주는 인증권한 에러 코드 (docs/spec/auth/backend/5-3-QUALITY-ERROR-HANDLING.md).
 *
 * <p>필터는 {@code domain} 패키지에 기대지 않으므로 {@code AuthErrorCode}와 따로 둔다. 접두사는 같은 {@code AUTH_}다.
 */
enum SecurityErrorCode implements ErrorCode {
    AUTH_PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "비밀번호를 변경한 뒤 이용할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    SecurityErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public String message() {
        return message;
    }
}
