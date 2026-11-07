package com.hackhu.hp.domain.post.dto;

import com.hackhu.hp.domain.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 공지 목록의 한 줄. 본문은 넣지 않는다 (4-2-3). */
@Schema(description = "공지 목록 항목")
public record NoticeSummaryResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "11월 정기 세미나 안내") String title,
        AuthorResponse author,
        @Schema(example = "2026-11-07T09:30:00Z") Instant createdAt,
        @Schema(description = "수정한 적이 없으면 createdAt과 같다", example = "2026-11-07T09:30:00Z")
                Instant updatedAt) {

    public static NoticeSummaryResponse of(Post post, String authorName) {
        return new NoticeSummaryResponse(
                post.getId(),
                post.getTitle(),
                new AuthorResponse(post.getAuthorId(), authorName),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
