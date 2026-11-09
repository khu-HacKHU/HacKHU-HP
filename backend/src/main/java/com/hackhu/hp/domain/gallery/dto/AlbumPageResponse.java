package com.hackhu.hp.domain.gallery.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/** 목록 응답 (docs/conventions/api.md 2절). Spring의 Page를 그대로 직렬화하지 않고 약속한 필드만 내려준다. */
@Schema(description = "앨범 목록 페이지")
public record AlbumPageResponse(
        List<AlbumSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static AlbumPageResponse from(Page<AlbumSummaryResponse> page) {
        return new AlbumPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
