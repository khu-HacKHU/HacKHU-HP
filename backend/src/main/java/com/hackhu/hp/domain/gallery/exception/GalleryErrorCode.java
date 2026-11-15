package com.hackhu.hp.domain.gallery.exception;

import com.hackhu.hp.global.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** 갤러리 도메인 에러 코드 (docs/conventions/api.md 3절). */
public enum GalleryErrorCode implements ErrorCode {
    UNSUPPORTED_FORMAT(
            HttpStatus.BAD_REQUEST, "GALLERY_UNSUPPORTED_FORMAT", "jpg, png, webp 형식만 올릴 수 있습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "GALLERY_FILE_TOO_LARGE", "사진 한 장은 10MB를 넘을 수 없습니다."),
    INVALID_UPLOAD(
            HttpStatus.BAD_REQUEST, "GALLERY_INVALID_UPLOAD", "올린 사진을 확인할 수 없습니다. 다시 올려주세요."),
    FUTURE_EVENT_DATE(
            HttpStatus.BAD_REQUEST, "GALLERY_FUTURE_EVENT_DATE", "활동 날짜는 오늘까지만 선택할 수 있습니다."),
    TOO_MANY_REQUESTS(
            HttpStatus.TOO_MANY_REQUESTS,
            "GALLERY_TOO_MANY_REQUESTS",
            "업로드 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    ALBUM_NOT_FOUND(HttpStatus.NOT_FOUND, "GALLERY_ALBUM_NOT_FOUND", "앨범을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    GalleryErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
