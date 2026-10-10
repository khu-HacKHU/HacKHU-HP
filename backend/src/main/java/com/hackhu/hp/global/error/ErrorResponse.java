package com.hackhu.hp.global.error;

import java.util.List;

/** 모든 에러 응답의 본문 (docs/conventions/api.md 3절). */
public record ErrorResponse(int status, String code, String message, List<FieldError> errors) {

    public record FieldError(String field, String reason) {}

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(
                errorCode.status().value(), errorCode.code(), errorCode.message(), errors);
    }
}
