package com.hackhu.hp.domain.auth.exception;

import com.hackhu.hp.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 로그인과 토큰 에러 코드 (docs/spec/auth/backend/5-3-QUALITY-ERROR-HANDLING.md 5-3-3). */
public enum AuthErrorCode implements ErrorCode {
    /**
     * 로그인 실패. 이메일이 없거나, 비밀번호가 틀리거나, 탈퇴한 계정이거나, 임시 비밀번호가 만료되었거나 이미 쓰였거나, 검증 직후 비밀번호가 바뀐 경우 모두 같은 코드와
     * 메시지다. 어느 쪽이 틀렸는지 알려 주지 않는다 (USER-STORIES.md 2절).
     */
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String message;

    AuthErrorCode(HttpStatus status, String message) {
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
