package com.hackhu.hp.domain.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 글 작성자. 이메일과 역할은 넣지 않는다 (4-2-1, auth 5-1-9). */
@Schema(description = "작성자")
public record AuthorResponse(
        @Schema(example = "7") Long id,
        @Schema(
                        description = "탈퇴한 작성자는 null. 화면은 \"탈퇴한 회원\"으로 보여 준다",
                        example = "홍길동",
                        nullable = true)
                String name) {}
