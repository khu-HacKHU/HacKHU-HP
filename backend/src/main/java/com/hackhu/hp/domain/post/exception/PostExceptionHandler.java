package com.hackhu.hp.domain.post.exception;

import com.hackhu.hp.global.error.ErrorCode;
import com.hackhu.hp.global.error.ErrorResponse;
import com.hackhu.hp.global.storage.UploadConfirmException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 게시판 전용 예외 처리. 공통 처리는 GlobalExceptionHandler가 하고, 첨부 확정 실패처럼 항목별 사유를 담아야 하는 것만 다룬다 (4-2-9). */
@RestControllerAdvice(basePackages = "com.hackhu.hp.domain.post")
public class PostExceptionHandler {

    @ExceptionHandler(UploadConfirmException.class)
    ResponseEntity<ErrorResponse> handleUploadConfirm(UploadConfirmException e) {
        List<ErrorResponse.FieldError> errors =
                e.getFailures().stream()
                        .map(failure ->
                                new ErrorResponse.FieldError(
                                        "uploadIds[" + failure.index() + "]", failure.reason()))
                        .toList();
        ErrorCode code = PostErrorCode.POST_INVALID_UPLOAD;
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, errors));
    }
}
