package com.hackhu.hp.domain.user.exception;

import com.hackhu.hp.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 회원 에러 코드 (docs/spec/auth/backend/5-3-QUALITY-ERROR-HANDLING.md). 인증권한팀 코드라 접두사는 {@code AUTH_}다.
 */
public enum UserErrorCode implements ErrorCode {
    AUTH_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    /** 관리 대상 회원이 없거나 탈퇴했다. 두 경우를 구분하지 않는다 (4-2-11~14). */
    AUTH_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    UserErrorCode(HttpStatus status, String message) {
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
