package com.hackhu.hp.global.auth;

/**
 * access 토큰을 받아 줄 수 없음. 응답은 이유와 상관없이 같은 {@code 401 UNAUTHORIZED}이고, 이유는 서버 로그에만 남긴다 (DECISIONS.md
 * A14).
 */
public class InvalidAccessTokenException extends Exception {

    public enum Reason {
        /** 형식이 깨졌거나, 서명이 틀렸거나, 알고리즘이 {@code HS256}이 아님 */
        INVALID,
        /** {@code iss}가 다름 */
        WRONG_ISSUER,
        /** 필수 클레임이 없거나 형식이 틀림 */
        BAD_CLAIMS,
        /** {@code exp}가 지남 */
        EXPIRED
    }

    private final Reason reason;

    InvalidAccessTokenException(Reason reason) {
        // 토큰 원문을 메시지에 넣지 않는다 (5-1-5).
        super(reason.name(), null, false, false);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
