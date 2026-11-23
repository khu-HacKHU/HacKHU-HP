package com.hackhu.hp.domain.user.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** 회원 목록 응답 (docs/conventions/api.md 2절, auth 4-2-10). Spring의 Page를 그대로 직렬화하지 않는다. */
public record AdminUserPageResponse(
        List<AdminUserResponse> content, int page, int size, long totalElements, int totalPages) {

    public static AdminUserPageResponse from(Page<AdminUserResponse> page) {
        return new AdminUserPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
