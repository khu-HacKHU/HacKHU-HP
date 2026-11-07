package com.hackhu.hp.domain.user.dto;

import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.global.auth.Role;
import java.time.Instant;

/** 가입 응답 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-3). 토큰을 주지 않는다. */
public record SignupResponse(long id, String email, String name, Role role, Instant createdAt) {

    public static SignupResponse from(User user) {
        return new SignupResponse(
                user.getId(), user.getEmail(), user.getName(), user.getRole(), user.getCreatedAt());
    }
}
