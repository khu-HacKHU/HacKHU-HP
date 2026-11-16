package com.hackhu.hp.domain.gallery.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 키는 테스트 안에서 새로 만든다. 실제 키를 테스트 코드에 넣지 않는다 (secure-coding 6절). */
class CloudFrontPhotoUrlResolverTest {

    private static final Instant NOW = Instant.parse("2026-11-07T03:00:00Z");

    private KeyPair keyPair;
    private CloudFrontPhotoUrlResolver resolver;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        resolver =
                new CloudFrontPhotoUrlResolver(
                        "https://cdn.example.com/",
                        "K2TESTKEYPAIR",
                        keyPair.getPrivate(),
                        Duration.ofHours(1),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 객체_키를_CDN_주소_뒤에_붙이고_서명_파라미터를_단다() {
        String url = resolver.resolve("gallery/abc.jpg");

        assertThat(url).startsWith("https://cdn.example.com/gallery/abc.jpg?");
        Map<String, String> query = query(url);
        assertThat(query).containsKeys("Expires", "Signature", "Key-Pair-Id");
        assertThat(query.get("Key-Pair-Id")).isEqualTo("K2TESTKEYPAIR");
    }

    @Test
    void 만료_시각은_지금부터_1시간_뒤다() {
        String url = resolver.resolve("gallery/abc.jpg");

        long expires = Long.parseLong(query(url).get("Expires"));
        assertThat(expires).isEqualTo(NOW.plus(Duration.ofHours(1)).getEpochSecond());
    }

    @Test
    void 서명은_공개_키로_검증된다() throws Exception {
        String url = resolver.resolve("gallery/abc.jpg");
        Map<String, String> query = query(url);

        // CloudFront canned policy 형식 (공백 없는 JSON). 서명 대상이 이것과 같아야 CloudFront가 URL을 연다.
        String policy =
                "{\"Statement\":[{\"Resource\":\"https://cdn.example.com/gallery/abc.jpg\","
                        + "\"Condition\":{\"DateLessThan\":{\"AWS:EpochTime\":"
                        + query.get("Expires")
                        + "}}}]}";
        byte[] signature =
                Base64.getDecoder()
                        .decode(
                                query.get("Signature")
                                        .replace('-', '+')
                                        .replace('_', '=')
                                        .replace('~', '/'));

        Signature verifier = Signature.getInstance("SHA1withRSA");
        verifier.initVerify(keyPair.getPublic());
        verifier.update(policy.getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(signature)).isTrue();
    }

    private static Map<String, String> query(String url) {
        Map<String, String> params = new HashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            params.put(kv[0], kv[1]);
        }
        return params;
    }
}
