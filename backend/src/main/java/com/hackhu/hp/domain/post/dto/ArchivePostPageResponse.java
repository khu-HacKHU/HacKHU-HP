package com.hackhu.hp.domain.post.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** 자료게시판 목록 한 페이지. 필드는 {@link PostPageResponse}와 같다 (api.md 2절). */
public record ArchivePostPageResponse(
        List<ArchivePostSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static ArchivePostPageResponse from(Page<ArchivePostSummaryResponse> page) {
        return new ArchivePostPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
