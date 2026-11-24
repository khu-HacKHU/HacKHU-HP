package com.hackhu.hp.domain.post.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** 공지 목록 한 페이지. Spring {@code Page}를 그대로 직렬화하지 않고 API 규약의 필드만 준다 (api.md 2절). */
public record NoticePageResponse(
        List<NoticeSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static NoticePageResponse from(Page<NoticeSummaryResponse> page) {
        return new NoticePageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
