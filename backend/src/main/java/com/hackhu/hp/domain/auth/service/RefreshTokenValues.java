package com.hackhu.hp.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * refresh 토큰 원문 만들기와 해시 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-5).
 *
 * <p>원문은 {@link SecureRandom}으로 만든 32바이트를 Base64URL로 인코딩한 문자열이고 JWT가 아니다. DB에는 SHA-256 해시만 둔다. 무작위
 * 값이라 느린 해시가 필요 없고, DB가 유출되어도 해시로는 토큰을 쓸 수 없다.
 */
final class RefreshTokenValues {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RefreshTokenValues() {}

    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 16진수 소문자 64자. {@code refresh_tokens.token_hash}에 저장하고 찾는 값이다. */
    static String hash(String token) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // 모든 Java 구현이 SHA-256을 제공한다 (MessageDigest 명세).
            throw new IllegalStateException(e);
        }
    }
}
