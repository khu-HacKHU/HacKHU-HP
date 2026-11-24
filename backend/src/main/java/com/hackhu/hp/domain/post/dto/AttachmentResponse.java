package com.hackhu.hp.domain.post.dto;

import com.hackhu.hp.domain.post.entity.Attachment;
import io.swagger.v3.oas.annotations.media.Schema;

/** 자료게시판 상세의 첨부파일 하나 (4-2-9). 객체 키와 다운로드 주소는 넣지 않는다 (DECISIONS.md B6). */
@Schema(description = "첨부파일")
public record AttachmentResponse(
        @Schema(example = "301") Long id,
        @Schema(description = "원래 파일 이름. 화면은 글자로 출력한다", example = "3주차_XSS.pdf") String fileName,
        @Schema(example = "application/pdf") String contentType,
        @Schema(description = "바이트", example = "1834201") long size) {

    public static AttachmentResponse from(Attachment attachment) {
        return new AttachmentResponse(
                attachment.getId(),
                attachment.getFileName(),
                attachment.getContentType(),
                attachment.getSizeBytes());
    }
}
