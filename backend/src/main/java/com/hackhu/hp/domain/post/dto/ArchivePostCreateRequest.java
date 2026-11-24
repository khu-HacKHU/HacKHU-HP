package com.hackhu.hp.domain.post.dto;

import com.hackhu.hp.domain.post.dto.validation.PostContent;
import com.hackhu.hp.domain.post.dto.validation.PostTitle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * 자료게시판 글 작성 요청 (4-2-9). 제목과 본문 규칙은 공지(4-2-5)와 같다.
 *
 * <p>첨부는 발급받은 {@code uploadId}로만 받는다. 객체 키, 형식, 크기, 파일 이름을 받지 않는다 (5-1-5, 5-1-7).
 */
@Schema(description = "자료게시판 글 작성 요청")
public record ArchivePostCreateRequest(
        @Schema(example = "웹 해킹 스터디 3주차 자료") @NotBlank(message = "제목을 입력해주세요.") @PostTitle
                String title,
        @Schema(example = "발표 자료와 실습 문제입니다.") @NotBlank(message = "본문을 입력해주세요.") @PostContent
                String content,
        @Schema(description = "첨부 업로드 주소 발급에서 받은 uploadId. 배열 순서가 표시 순서다")
                @Size(max = 5, message = "첨부파일은 5개까지 붙일 수 있습니다.")
                List<@NotNull UUID> uploadIds) {

    public ArchivePostCreateRequest {
        title = title == null ? null : title.strip();
        content = content == null ? null : content.replace("\r\n", "\n").replace('\r', '\n');
        uploadIds = uploadIds == null ? List.of() : uploadIds;
    }
}
