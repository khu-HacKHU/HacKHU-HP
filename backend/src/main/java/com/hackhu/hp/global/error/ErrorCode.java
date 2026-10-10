package com.hackhu.hp.global.error;

import org.springframework.http.HttpStatus;

/**
 * API 에러 코드. 응답 형식은 docs/conventions/api.md 3절을 따른다.
 *
 * <p>도메인 에러는 도메인 패키지에 이 인터페이스를 구현한 enum을 따로 만든다 (예: {@code domain/post/exception/PostErrorCode}).
 * 팀마다 공통 파일을 고치지 않아도 되게 하려는 것이다.
 */
public interface ErrorCode {

    HttpStatus status();

    /** 클라이언트가 분기에 쓰는 코드. UPPER_SNAKE_CASE, 도메인 접두사를 붙인다 (예: POST_NOT_FOUND). */
    String code();

    /** 사용자에게 보여줄 한국어 메시지. 내부 구현이나 존재 여부를 드러내지 않는다. */
    String message();
}
