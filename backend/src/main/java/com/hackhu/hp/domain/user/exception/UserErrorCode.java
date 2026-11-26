package com.hackhu.hp.domain.user.exception;

import com.hackhu.hp.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 회원 에러 코드 (docs/spec/auth/backend/5-3-QUALITY-ERROR-HANDLING.md). 인증권한팀 코드라 접두사는 {@code AUTH_}다.
 */
public enum UserErrorCode implements ErrorCode {
    AUTH_DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    /** 관리 대상 회원이 없거나 탈퇴했다. 두 경우를 구분하지 않는다 (4-2-11~14). */
    AUTH_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    /** 승인과 거절의 대상이 승인 대기 회원이 아니다. 이미 처리된 경우다 (4-2-11, 4-2-12). */
    AUTH_USER_NOT_PENDING(HttpStatus.CONFLICT, "승인 대기 중인 회원이 아닙니다."),
    /** 승인 대기 회원은 역할 변경의 대상이 아니다. 승인은 가입 승인 API로만 한다 (4-2-13, 5-1-3). */
    AUTH_TARGET_PENDING(HttpStatus.CONFLICT, "승인 대기 회원의 역할은 바꿀 수 없습니다. 가입 승인으로 처리해주세요."),
    /** 활성 관리자가 대상 한 명뿐이다 (5-2-4). */
    AUTH_LAST_ADMIN(HttpStatus.CONFLICT, "활성 관리자가 한 명뿐이라 처리할 수 없습니다."),
    /** 처리 도중 대상의 역할이 계속 바뀌었다. 다시 시도하면 된다 (5-2-4). */
    AUTH_ROLE_CHANGE_CONFLICT(HttpStatus.CONFLICT, "회원의 역할이 처리 도중 바뀌었습니다. 다시 시도해주세요.");

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
