package com.hackhu.hp.global.auth;

import java.util.UUID;

/**
 * 서명과 만료를 검증한 access 토큰의 내용. 역할은 담지 않는다. 역할은 요청마다 DB에서 읽는다 (DECISIONS.md A2).
 *
 * @param userId {@code sub}
 * @param sessionId {@code sid}. 로그인 세션 ID
 */
public record AccessTokenClaims(long userId, UUID sessionId) {}
