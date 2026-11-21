package com.hackhu.hp.domain.post.dto;

import com.hackhu.hp.domain.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 자유게시판 목록의 한 줄. 본문은 넣지 않는다 (4-2-8). 자료게시판은 첨부 수를 더한 응답을 따로 쓴다 (#55). */
@Schema(description = "글 목록 항목")
public record PostSummaryResponse(
        @Schema(example = "51") Long id,
        @Schema(example = "이번 주 스터디 후기") String title,
        AuthorResponse author,
        @Schema(example = "2026-11-16T09:30:00Z") Instant createdAt,
        @Schema(description = "수정한 적이 없으면 createdAt과 같다", example = "2026-11-16T09:30:00Z")
                Instant updatedAt) {

    public static PostSummaryResponse of(Post post, String authorName) {
        return new PostSummaryResponse(
                post.getId(),
                post.getTitle(),
                new AuthorResponse(post.getAuthorId(), authorName),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
