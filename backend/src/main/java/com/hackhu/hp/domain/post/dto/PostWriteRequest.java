package com.hackhu.hp.domain.post.dto;

import com.hackhu.hp.domain.post.dto.validation.PostContent;
import com.hackhu.hp.domain.post.dto.validation.PostTitle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 자유게시판 글 작성, 수정 요청 (4-2-8). 필드와 규칙은 공지 요청(4-2-5)과 같다.
 *
 * <p>{@code title}, {@code content}만 받는다. {@code authorId}, {@code boardType}, {@code id}를 두지 않는다
 * (5-1-5, CWE-915). 제목은 앞뒤 공백을 지우고, 본문은 줄바꿈을 {@code \n}으로 맞춘 뒤 검증한다 (4-2-1).
 */
@Schema(description = "글 작성, 수정 요청")
public record PostWriteRequest(
        @Schema(example = "이번 주 스터디 후기") @NotBlank(message = "제목을 입력해주세요.") @PostTitle String title,
        @Schema(example = "XSS 실습이 재밌었습니다.") @NotBlank(message = "본문을 입력해주세요.") @PostContent
                String content) {

    public PostWriteRequest {
        title = title == null ? null : title.strip();
        content = content == null ? null : content.replace("\r\n", "\n").replace('\r', '\n');
    }
}
