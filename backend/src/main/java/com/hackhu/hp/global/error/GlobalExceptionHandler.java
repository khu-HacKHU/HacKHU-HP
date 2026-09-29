package com.hackhu.hp.global.error;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 예외를 공통 에러 응답으로 바꾼다.
 *
 * <p>응답에는 {@link ErrorCode}의 고정 메시지만 싣는다. 예외 메시지, 스택 트레이스, SQL은 서버 로그에만 남긴다 (CWE-209).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return respond(e.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> errors =
                e.getBindingResult().getFieldErrors().stream()
                        .map(f -> new ErrorResponse.FieldError(f.getField(), f.getDefaultMessage()))
                        .toList();
        ErrorCode code = CommonErrorCode.INVALID_INPUT;
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code, errors));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        return respond(CommonErrorCode.INVALID_INPUT);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
        return respond(CommonErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return respond(CommonErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return respond(CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code) {
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
    }
}
