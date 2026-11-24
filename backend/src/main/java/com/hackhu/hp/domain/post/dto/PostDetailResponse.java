package com.hackhu.hp.domain.post.dto;

import com.hackhu.hp.domain.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 자유게시판 글 상세 (4-2-8). 모양은 공지 상세(4-2-4)와 같다. */
@Schema(description = "글 상세")
public record PostDetailResponse(
        @Schema(example = "51") Long id,
        @Schema(example = "이번 주 스터디 후기") String title,
        @Schema(description = "받은 글자 그대로. 화면은 HTML로 해석하지 않고 글자로 출력한다 (5-1-10)") String content,
        AuthorResponse author,
        @Schema(example = "2026-11-16T09:30:00Z") Instant createdAt,
        @Schema(description = "수정한 적이 없으면 createdAt과 같다") Instant updatedAt) {

    public static PostDetailResponse of(Post post, String authorName) {
        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                new AuthorResponse(post.getAuthorId(), authorName),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
