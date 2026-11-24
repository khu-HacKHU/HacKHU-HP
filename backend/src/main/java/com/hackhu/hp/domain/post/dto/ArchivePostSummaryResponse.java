package com.hackhu.hp.domain.post.dto;

import com.hackhu.hp.domain.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 자료게시판 목록의 한 줄 (4-2-9). 자유게시판 목록 항목에 첨부 수가 더해진다. */
@Schema(description = "자료게시판 목록 항목")
public record ArchivePostSummaryResponse(
        @Schema(example = "77") Long id,
        @Schema(example = "웹 해킹 스터디 3주차 자료") String title,
        AuthorResponse author,
        @Schema(example = "2") long attachmentCount,
        @Schema(example = "2026-11-20T11:00:00Z") Instant createdAt,
        @Schema(description = "수정한 적이 없으면 createdAt과 같다", example = "2026-11-20T11:00:00Z")
                Instant updatedAt) {

    public static ArchivePostSummaryResponse of(
            Post post, String authorName, long attachmentCount) {
        return new ArchivePostSummaryResponse(
                post.getId(),
                post.getTitle(),
                new AuthorResponse(post.getAuthorId(), authorName),
                attachmentCount,
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
