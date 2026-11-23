package com.hackhu.hp.domain.user.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영진의 회원 관리 API (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-10~14).
 *
 * <p>{@code /api/v1/admin/**}는 SecurityConfig가 운영진 이상으로 막고, 역할 변경만 관리자로 막는다.
 */
@Tag(name = "회원 관리", description = "운영진 이상. 역할 변경은 관리자")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {}
