package com.hackhu.hp.domain.post.exception;

import com.hackhu.hp.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 게시판 에러 코드 (docs/conventions/api.md 3절, 4-2-INTERACTION-CONTRACT.md 4-2-1). */
public enum PostErrorCode implements ErrorCode {
    /** 글이 없거나, 다른 게시판의 글이다. 다른 게시판 글의 존재 여부를 알려 주지 않는다 (4-2-4). */
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    PostErrorCode(HttpStatus status, String message) {
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
