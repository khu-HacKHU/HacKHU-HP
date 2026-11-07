package com.hackhu.hp.domain.post.dto;

import com.hackhu.hp.domain.post.entity.Post;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 공지 상세 (4-2-4). */
@Schema(description = "공지 상세")
public record NoticeDetailResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "11월 정기 세미나 안내") String title,
        @Schema(
                        description = "받은 글자 그대로. 화면은 HTML로 해석하지 않고 글자로 출력한다 (5-1-6)",
                        example = "일시: 11월 14일 19시\n장소: 동아리방")
                String content,
        AuthorResponse author,
        @Schema(example = "2026-11-07T09:30:00Z") Instant createdAt,
        @Schema(description = "수정한 적이 없으면 createdAt과 같다", example = "2026-11-07T10:05:00Z")
                Instant updatedAt) {

    public static NoticeDetailResponse of(Post post, String authorName) {
        return new NoticeDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                new AuthorResponse(post.getAuthorId(), authorName),
                post.getCreatedAt(),
                post.getUpdatedAt());
    }
}
