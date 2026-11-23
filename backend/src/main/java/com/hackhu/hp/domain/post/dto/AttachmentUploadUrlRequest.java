package com.hackhu.hp.domain.post.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 첨부 업로드 주소 발급 요청 (4-2-10). 확장자, 형식, 크기의 허용 여부와 짝은 서비스가 확인하고 정해진 에러 코드로 거부한다. */
@Schema(description = "첨부 업로드 주소 발급 요청")
public record AttachmentUploadUrlRequest(
        @NotNull(message = "올릴 파일을 보내주세요.")
                @Size(min = 1, max = 5, message = "파일은 한 번에 1개 이상 5개 이하로 올릴 수 있습니다.")
                List<@Valid @NotNull FileSpec> files) {

    public record FileSpec(
            @Schema(example = "3주차_XSS.pdf") @NotBlank(message = "파일 이름을 입력해주세요.") String fileName,
            @Schema(example = "application/pdf") @NotBlank(message = "형식을 입력해주세요.")
                    String contentType,
            @Schema(description = "바이트", example = "1834201")
                    @Positive(message = "파일 크기가 올바르지 않습니다.")
                    long size) {}
}
