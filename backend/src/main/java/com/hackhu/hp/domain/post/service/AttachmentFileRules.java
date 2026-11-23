package com.hackhu.hp.domain.post.service;

import com.hackhu.hp.domain.post.dto.validation.BidiControls;
import com.hackhu.hp.domain.post.exception.PostErrorCode;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.error.CommonErrorCode;
import java.util.Locale;
import java.util.Map;

/** 첨부 업로드 주소 발급 전 검사 (4-2-10, 5-1-6, 5-1-7 1단계). 허용하지 않는 형식과 20MB 초과를 S3에 올리기 전에 거부한다. */
final class AttachmentFileRules {

    static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;
    static final int MAX_FILE_NAME_LENGTH = 200;

    /** 확장자와 짝이 맞는 MIME 타입. DB 제약(ck_attachment_content_type)과 같은 목록이다. */
    static final Map<String, String> CONTENT_TYPES =
            Map.of(
                    "pdf", "application/pdf",
                    "docx",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "pptx",
                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private AttachmentFileRules() {}

    /**
     * 원래 파일 이름을 정리해 돌려준다. 경로 부분을 지우고, 제어 문자와 양방향 텍스트 제어 문자가 있으면 거부한다.
     *
     * <p>줄바꿈은 {@code Content-Disposition} 헤더를 깨뜨리고(CWE-113), 양방향 문자는 다른 확장자 파일을 문서처럼 보이게 한다
     * (5-1-6). 사유에 입력값을 넣지 않는다 (5-1-5).
     */
    static String sanitizeFileName(String fileName) {
        String name =
                fileName.substring(
                        Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\')) + 1);
        boolean hasForbidden =
                name.codePoints()
                        .anyMatch(
                                cp -> Character.isISOControl(cp) || BidiControls.isBidiControl(cp));
        int length = name.codePointCount(0, name.length());
        if (hasForbidden || name.isBlank() || length > MAX_FILE_NAME_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return name;
    }

    /** 확장자(마지막 {@code .} 뒤, 대소문자 무시)와 형식이 허용 목록에 있고 서로 짝이 맞는지, 크기가 20MB 이하인지 본다. */
    static void checkFormatAndSize(String fileName, String contentType, long size) {
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expected = CONTENT_TYPES.get(extension);
        if (expected == null || !expected.equals(contentType)) {
            throw new BusinessException(PostErrorCode.POST_UNSUPPORTED_FORMAT);
        }
        if (size > MAX_SIZE_BYTES) {
            throw new BusinessException(PostErrorCode.POST_FILE_TOO_LARGE);
        }
    }
}
