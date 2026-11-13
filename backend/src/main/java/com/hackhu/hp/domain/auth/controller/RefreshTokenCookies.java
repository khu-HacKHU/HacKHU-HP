package com.hackhu.hp.domain.auth.controller;

import com.hackhu.hp.domain.auth.service.IssuedTokens.NewRefreshToken;
import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * refresh 토큰 쿠키 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-1, 5-1-5).
 *
 * <ul>
 *   <li>{@code HttpOnly}: JavaScript가 읽지 못한다. XSS로 장기 토큰을 빼앗기지 않게 한다.
 *   <li>{@code Secure}, {@code SameSite=Strict}: HTTPS로만, 같은 사이트 요청에만 실린다.
 *   <li>{@code Path=/api/v1/auth}: 인증 API에만 실리고 다른 API 요청에는 실리지 않는다.
 *   <li>{@code Domain}을 두지 않는다. API 호스트에만 실리고 같은 등록 도메인의 다른 서브도메인에는 실리지 않는다.
 * </ul>
 */
final class RefreshTokenCookies {

    static final String NAME = "refresh_token";
    static final String PATH = "/api/v1/auth";

    private RefreshTokenCookies() {}

    static String issue(NewRefreshToken token) {
        return builder(token.value(), token.maxAge()).toString();
    }

    /** 같은 이름과 경로로 {@code Max-Age=0}을 보내 지운다. */
    static String clear() {
        return builder("", Duration.ZERO).toString();
    }

    private static ResponseCookie builder(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
