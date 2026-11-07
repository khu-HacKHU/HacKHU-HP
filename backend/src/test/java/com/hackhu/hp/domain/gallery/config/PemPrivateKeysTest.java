package com.hackhu.hp.domain.gallery.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PemPrivateKeysTest {

    @Test
    void PKCS8_PEM을_읽는다() throws Exception {
        KeyPair keyPair = newKeyPair();
        String pem = toPem(keyPair.getPrivate(), "\n");

        assertThat(PemPrivateKeys.parseRsa(pem).getEncoded())
                .isEqualTo(keyPair.getPrivate().getEncoded());
    }

    @Test
    void 줄바꿈이_역슬래시_n으로_들어와도_읽는다() throws Exception {
        KeyPair keyPair = newKeyPair();
        String pem = toPem(keyPair.getPrivate(), "\\n");

        assertThat(PemPrivateKeys.parseRsa(pem).getEncoded())
                .isEqualTo(keyPair.getPrivate().getEncoded());
    }

    @Test
    void PKCS1_PEM은_변환_방법을_안내하고_거부한다() {
        String pkcs1 = "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----";

        assertThatThrownBy(() -> PemPrivateKeys.parseRsa(pkcs1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openssl pkcs8");
    }

    @Test
    void 깨진_키는_내용을_노출하지_않고_거부한다() {
        String broken = "-----BEGIN PRIVATE KEY-----\nnot-base64!!\n-----END PRIVATE KEY-----";

        assertThatThrownBy(() -> PemPrivateKeys.parseRsa(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not-base64");
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String toPem(PrivateKey key, String newline) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----";
        return pem.replace("\n", newline);
    }
}
