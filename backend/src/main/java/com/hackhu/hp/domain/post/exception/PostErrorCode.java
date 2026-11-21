package com.hackhu.hp.domain.post.exception;

import com.hackhu.hp.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 게시판 에러 코드 (docs/conventions/api.md 3절, 4-2-INTERACTION-CONTRACT.md 4-2-1). */
public enum PostErrorCode implements ErrorCode {
    /** 글이 없거나, 다른 게시판의 글이다. 다른 게시판 글의 존재 여부를 알려 주지 않는다 (4-2-4). */
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "글을 찾을 수 없습니다."),
    /** 역할은 맞지만 작성자도 관리자도 아니다. 역할이 부족한 403 {@code FORBIDDEN}과 구분한다 (4-2-6). */
    POST_FORBIDDEN(HttpStatus.FORBIDDEN, "이 글을 고치거나 지울 권한이 없습니다."),
    /** 발급 단계에서 확장자, 형식, 둘의 짝이 허용 목록에 없다 (4-2-10). */
    POST_UNSUPPORTED_FORMAT(HttpStatus.BAD_REQUEST, "pdf, docx, pptx, xlsx 파일만 올릴 수 있습니다."),
    /** 발급 단계에서 20MB를 넘는다 (4-2-10). */
    POST_FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "파일 하나는 20MB를 넘을 수 없습니다."),
    /** 확정 실패. {@code errors}에 실패한 {@code uploadId}별 사유를 담는다 (4-2-9, 5-1-8). */
    POST_INVALID_UPLOAD(HttpStatus.BAD_REQUEST, "올린 파일을 확인할 수 없습니다. 다시 올려주세요."),
    /** 남길 첨부와 새 업로드를 합쳐 5개를 넘는다 (4-2-9). */
    POST_TOO_MANY_ATTACHMENTS(HttpStatus.BAD_REQUEST, "첨부파일은 글 하나에 5개까지 붙일 수 있습니다."),
    /** 그 글에 없는 첨부 ID다. 다른 글의 첨부 ID도 여기에 걸린다 (4-2-11). */
    POST_ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다.");

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
