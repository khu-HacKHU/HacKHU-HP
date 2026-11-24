package com.hackhu.hp.domain.gallery.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 업로드 주소 발급 요청 (docs/spec/gallery/backend/4-2 3절). */
public record UploadUrlRequest(
        @Valid @Size(min = 1, max = 20, message = "사진은 1장 이상 20장 이하로 올릴 수 있습니다.")
                List<FileSpec> files) {

    public record FileSpec(
            @NotBlank(message = "형식을 입력해주세요.") String contentType,
            @Positive(message = "파일 크기가 올바르지 않습니다.") long size) {}
}
