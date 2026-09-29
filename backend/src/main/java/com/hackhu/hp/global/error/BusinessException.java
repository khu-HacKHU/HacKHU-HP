package com.hackhu.hp.global.error;

/**
 * 예상 가능한 실패(권한 없음, 리소스 없음 등)를 알리는 예외. {@link GlobalExceptionHandler}가 {@link ErrorCode}에 맞는 응답으로
 * 바꾼다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.code());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
