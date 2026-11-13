package com.hackhu.hp.domain.auth.repository;

import com.hackhu.hp.global.auth.Role;
import java.time.Instant;

/**
 * 요청마다 하는 인증에서 로그인 세션과 계정을 함께 읽은 결과 ({@link LoginSessionRepository#findPrincipal}).
 *
 * <p>JPQL 생성자 표현식이 엔티티 필드 타입과 같은 래퍼 타입을 받는다.
 */
public record SessionPrincipal(
        Long userId,
        Instant sessionExpiresAt,
        Instant sessionRevokedAt,
        Role role,
        Boolean mustChangePassword,
        Instant withdrawnAt) {}
