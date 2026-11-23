package com.hackhu.hp.domain.user.dto;

import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.global.auth.Role;
import java.time.Instant;

/**
 * 운영진이 보는 회원 한 명 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-10). 가입 승인(4-2-11)과 역할
 * 변경(4-2-13)의 응답도 이 형식이다.
 *
 * <p>이메일은 운영진 이상에게만 보이므로 이 응답을 다른 기능의 응답에 재사용하지 않는다 (5-1-9).
 */
public record AdminUserResponse(
        long id, String email, String name, Role role, Instant createdAt, Instant approvedAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getCreatedAt(),
                user.getApprovedAt());
    }
}
