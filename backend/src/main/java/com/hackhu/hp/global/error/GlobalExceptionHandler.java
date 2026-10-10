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
        // Spring MVC 표준 예외(415, 406, ResponseStatusException 등)는 자기 상태 코드를 그대로 쓴다.
        // 클라이언트 잘못이라 ERROR로 남기지 않는다. 누구나 ERROR 로그를 쌓을 수 있게 되기 때문이다 (CWE-779).
        if (e instanceof org.springframework.web.ErrorResponse er
                && er.getStatusCode().is4xxClientError()) {
            log.debug("클라이언트 요청 오류: {}", e.getClass().getSimpleName());
            return respond(CommonErrorCode.from(er.getStatusCode()));
        }
        // TODO(인증·권한 팀): Spring Security를 붙이면 AccessDeniedException(403),
        // AuthenticationException(401) 핸들러를 여기 위에 추가한다. 없으면 500이 된다.
        log.error("처리되지 않은 예외", e);
        return respond(CommonErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode code) {
        return ResponseEntity.status(code.status()).body(ErrorResponse.of(code));
    }
}
