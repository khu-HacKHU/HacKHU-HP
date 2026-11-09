package com.hackhu.hp.domain.auth.service;

import com.hackhu.hp.domain.auth.dto.TokenResponse;
import java.time.Duration;

/**
 * 로그인이나 refresh로 발급한 토큰.
 *
 * @param body 응답 본문
 * @param refreshToken 새로 준 refresh 토큰. 동시 요청으로 access 토큰만 다시 준 경우는 null이고 {@code Set-Cookie}를 보내지
 *     않는다 (4-2-5)
 */
public record IssuedTokens(TokenResponse body, NewRefreshToken refreshToken) {

    /**
     * 쿠키로 줄 refresh 토큰.
     *
     * @param value 원문. 응답 쿠키에만 싣고 저장하거나 로그에 남기지 않는다
     * @param maxAge 쿠키의 {@code Max-Age}. 14일과 로그인 세션의 남은 수명 중 짧은 쪽이다 (4-2-1)
     */
    public record NewRefreshToken(String value, Duration maxAge) {

        @Override
        public String toString() {
            return "NewRefreshToken[value=***, maxAge=" + maxAge + "]";
        }
    }
}
