package com.hackhu.hp.domain.post.dto;

import com.hackhu.hp.domain.post.dto.validation.PostContent;
import com.hackhu.hp.domain.post.dto.validation.PostTitle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 공지 작성, 수정 요청 (docs/spec/board/backend/4-2-INTERACTION-CONTRACT.md 4-2-5, 4-2-6).
 *
 * <p>계약에 적힌 두 필드만 받는다. {@code authorId}, {@code boardType}, {@code id}를 두지 않는다. 본문에 넣어 보내도 무시되고
 * 작성자는 로그인한 사람이 된다 (5-1-5, CWE-915).
 *
 * <p>정규화를 생성자에서 먼저 하고 그 결과를 검증한다. 제목은 앞뒤 공백을 지우고, 본문은 줄바꿈을 {@code \n}으로 맞춘다 (4-2-1).
 */
@Schema(description = "공지 작성, 수정 요청")
public record NoticeWriteRequest(
        @Schema(example = "11월 정기 세미나 안내") @NotBlank(message = "제목을 입력해주세요.") @PostTitle
                String title,
        @Schema(example = "일시: 11월 14일 19시\n장소: 동아리방")
                @NotBlank(message = "본문을 입력해주세요.")
                @PostContent
                String content) {

    public NoticeWriteRequest {
        title = title == null ? null : title.strip();
        content = content == null ? null : content.replace("\r\n", "\n").replace('\r', '\n');
    }
}
