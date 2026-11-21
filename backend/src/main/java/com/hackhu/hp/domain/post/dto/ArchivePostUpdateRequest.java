package com.hackhu.hp.domain.post.dto;

import com.hackhu.hp.domain.post.dto.validation.PostContent;
import com.hackhu.hp.domain.post.dto.validation.PostTitle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

/**
 * 자료게시판 글 수정 요청 (4-2-9, DECISIONS.md B10).
 *
 * <p>남길 기존 첨부({@code attachmentIds})와 새로 붙일 업로드({@code uploadIds})를 함께 받는다. 표시 순서는 {@code
 * attachmentIds} 다음 {@code uploadIds} 순서다. 합쳐서 5개를 넘는지는 글 행을 잠근 뒤 서비스가 센다 (5-1-8).
 */
@Schema(description = "자료게시판 글 수정 요청")
public record ArchivePostUpdateRequest(
        @Schema(example = "웹 해킹 스터디 3주차 자료 (수정)") @NotBlank(message = "제목을 입력해주세요.") @PostTitle
                String title,
        @Schema(example = "실습 문제를 바꿨습니다.") @NotBlank(message = "본문을 입력해주세요.") @PostContent
                String content,
        @Schema(description = "남길 기존 첨부 ID. 바꾸지 않을 때도 모두 보낸다. 빈 배열이면 기존 첨부를 모두 뺀다")
                @NotNull(message = "남길 첨부파일 목록을 보내주세요.")
                List<@NotNull @Positive Long> attachmentIds,
        @Schema(description = "새로 붙일 uploadId") List<@NotNull UUID> uploadIds) {

    public ArchivePostUpdateRequest {
        title = title == null ? null : title.strip();
        content = content == null ? null : content.replace("\r\n", "\n").replace('\r', '\n');
        uploadIds = uploadIds == null ? List.of() : uploadIds;
    }
}
