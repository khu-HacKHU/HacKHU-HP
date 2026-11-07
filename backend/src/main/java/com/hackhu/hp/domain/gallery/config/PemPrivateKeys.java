package com.hackhu.hp.domain.gallery.config;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** PEM 문자열을 RSA 개인 키로 읽는다. */
final class PemPrivateKeys {

    private static final String PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS8_FOOTER = "-----END PRIVATE KEY-----";

    private PemPrivateKeys() {}

    static PrivateKey parseRsa(String pem) {
        // 환경변수로 넘길 때 줄바꿈이 \n 두 글자로 들어오는 경우도 받는다.
        String normalized = pem.replace("\\n", "\n").strip();
        if (!normalized.startsWith(PKCS8_HEADER) || !normalized.contains(PKCS8_FOOTER)) {
            // 키 내용은 예외 메시지에 넣지 않는다 (CWE-532).
            throw new IllegalStateException(
                    "gallery.cdn.private-key는 PKCS#8 PEM(-----BEGIN PRIVATE KEY-----)이어야 합니다."
                            + " PKCS#1 키는 openssl pkcs8 -topk8 -nocrypt로 바꿔서 넣으세요.");
        }
        String base64 =
                normalized
                        .substring(PKCS8_HEADER.length(), normalized.indexOf(PKCS8_FOOTER))
                        .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalStateException("gallery.cdn.private-key를 RSA 개인 키로 읽을 수 없습니다.");
        }
    }
}
