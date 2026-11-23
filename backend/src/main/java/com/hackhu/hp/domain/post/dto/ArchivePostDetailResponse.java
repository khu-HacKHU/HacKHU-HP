package com.hackhu.hp.domain.post.dto;

import com.hackhu.hp.domain.post.entity.Attachment;
import com.hackhu.hp.domain.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** 자료게시판 글 상세 (4-2-9). 자유게시판 상세에 첨부 목록이 더해진다. */
@Schema(description = "자료게시판 글 상세")
public record ArchivePostDetailResponse(
        @Schema(example = "77") Long id,
        @Schema(example = "웹 해킹 스터디 3주차 자료") String title,
        @Schema(description = "받은 글자 그대로. 화면은 HTML로 해석하지 않고 글자로 출력한다 (5-1-10)") String content,
        AuthorResponse author,
        @Schema(description = "표시 순서대로. 없으면 빈 배열") List<AttachmentResponse> attachments,
        @Schema(example = "2026-11-20T11:00:00Z") Instant createdAt,
        @Schema(description = "수정한 적이 없으면 createdAt과 같다") Instant updatedAt) {

    public static ArchivePostDetailResponse of(
            Post post, String authorName, List<Attachment> attachments) {
        return new ArchivePostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                new AuthorResponse(post.getAuthorId(), authorName),
                attachments.stream().map(AttachmentResponse::from).toList(),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
