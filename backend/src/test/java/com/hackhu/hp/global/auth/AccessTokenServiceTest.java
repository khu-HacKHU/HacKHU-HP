package com.hackhu.hp.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hackhu.hp.global.auth.InvalidAccessTokenException.Reason;
import com.hackhu.hp.support.MutableClock;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AccessTokenServiceTest {

    private static final UUID SESSION_ID = UUID.fromString("1b4e28ba-2fa1-11d2-883f-0016d3cca427");

    private final byte[] keyBytes = randomBytes(32);
    private final SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
    private final MutableClock clock = new MutableClock();
    private final AccessTokenService service = new AccessTokenService(key, clock);

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static Reason reasonOf(AccessTokenService service, String token) {
        try {
            service.verify(token);
        } catch (InvalidAccessTokenException e) {
            return e.reason();
        }
        throw new AssertionError("거부되어야 함");
    }

    private JWTClaimsSet.Builder validClaims() {
        Date now = Date.from(clock.instant());
        return new JWTClaimsSet.Builder()
                .issuer("hackhu-hp")
                .subject("7")
                .claim("sid", SESSION_ID.toString())
                .issueTime(now)
                .expirationTime(Date.from(clock.instant().plus(Duration.ofMinutes(15))));
    }

    private String sign(JWSAlgorithm algorithm, byte[] secret, JWTClaimsSet claims)
            throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
        jwt.sign(new MACSigner(secret));
        return jwt.serialize();
    }

    @Test
    void 발급한_토큰을_검증하면_사용자와_세션이_나온다() throws Exception {
        AccessTokenService.IssuedAccessToken issued = service.issue(7L, SESSION_ID);

        assertThat(issued.expiresInSeconds()).isEqualTo(900);
        assertThat(service.verify(issued.value())).isEqualTo(new AccessTokenClaims(7L, SESSION_ID));
    }

    @Test
    void 클레임은_iss_sub_sid_iat_exp만_있고_역할과_개인정보가_없다() throws Exception {
        String token = service.issue(7L, SESSION_ID).value();
        Map<String, Object> claims = SignedJWT.parse(token).getJWTClaimsSet().getClaims();

        assertThat(claims).containsOnlyKeys("iss", "sub", "sid", "iat", "exp");
        assertThat(SignedJWT.parse(token).getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
    }

    @Test
    void 만료된_토큰은_EXPIRED로_거부한다() {
        String token = service.issue(7L, SESSION_ID).value();
        clock.advance(Duration.ofMinutes(15));

        assertThat(reasonOf(service, token)).isEqualTo(Reason.EXPIRED);
    }

    @Test
    void 만료_직전_토큰은_받아_준다() throws Exception {
        String token = service.issue(7L, SESSION_ID).value();
        clock.advance(Duration.ofMinutes(15).minusSeconds(1));

        assertThat(service.verify(token).userId()).isEqualTo(7L);
    }

    @Test
    void 다른_키로_서명한_토큰은_거부한다_T8() {
        String token = service.issue(7L, SESSION_ID).value();
        AccessTokenService rotated =
                new AccessTokenService(new SecretKeySpec(randomBytes(32), "HmacSHA256"), clock);

        assertThat(reasonOf(rotated, token)).isEqualTo(Reason.INVALID);
    }

    @Test
    void alg_none_토큰은_거부한다() {
        String token = new PlainJWT(validClaims().build()).serialize();

        assertThat(reasonOf(service, token)).isEqualTo(Reason.INVALID);
    }

    @Test
    void 같은_키여도_HS256이_아닌_알고리즘은_거부한다() throws Exception {
        // HS512는 64바이트 이상의 키가 필요하다. 앞 32바이트가 같은 키로 서명해 알고리즘만 다르게 한다.
        byte[] longKey = new byte[64];
        System.arraycopy(keyBytes, 0, longKey, 0, 32);
        AccessTokenService longKeyService =
                new AccessTokenService(new SecretKeySpec(longKey, "HmacSHA256"), clock);
        String token = sign(JWSAlgorithm.HS512, longKey, validClaims().build());

        assertThat(reasonOf(longKeyService, token)).isEqualTo(Reason.INVALID);
    }

    @Test
    void 발급자가_다르면_거부한다() throws Exception {
        String token = sign(JWSAlgorithm.HS256, keyBytes, validClaims().issuer("other").build());

        assertThat(reasonOf(service, token)).isEqualTo(Reason.WRONG_ISSUER);
    }

    @Test
    void sid가_없거나_sub가_숫자가_아니거나_exp가_없으면_거부한다() throws Exception {
        String noSid = sign(JWSAlgorithm.HS256, keyBytes, validClaims().claim("sid", null).build());
        String badSub = sign(JWSAlgorithm.HS256, keyBytes, validClaims().subject("admin").build());
        String noExp =
                sign(JWSAlgorithm.HS256, keyBytes, validClaims().expirationTime(null).build());

        assertThat(reasonOf(service, noSid)).isEqualTo(Reason.BAD_CLAIMS);
        assertThat(reasonOf(service, badSub)).isEqualTo(Reason.BAD_CLAIMS);
        assertThat(reasonOf(service, noExp)).isEqualTo(Reason.BAD_CLAIMS);
    }

    @Test
    void 형식이_깨진_토큰은_거부한다() {
        assertThat(reasonOf(service, "not-a-jwt")).isEqualTo(Reason.INVALID);
        assertThat(reasonOf(service, "a.b.c")).isEqualTo(Reason.INVALID);
    }

    @Test
    void 서명_키가_없거나_짧거나_Base64가_아니면_시작하지_않는다_I3() {
        String shortKey = Base64.getEncoder().encodeToString(randomBytes(31));

        assertThatThrownBy(() -> AccessTokenService.signingKey(new JwtProperties(null, false)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AccessTokenService.signingKey(new JwtProperties("  ", false)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AccessTokenService.signingKey(new JwtProperties(shortKey, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(shortKey);
        assertThatThrownBy(() -> AccessTokenService.signingKey(new JwtProperties("%%%", false)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키_길이가_32바이트_이상이면_받는다() {
        String key = Base64.getEncoder().encodeToString(randomBytes(32));

        assertThat(AccessTokenService.signingKey(new JwtProperties(key, false)).getEncoded())
                .hasSize(32);
    }

    @Test
    void 허용한_경우에만_키가_없을_때_무작위_키를_만든다() {
        assertThat(AccessTokenService.signingKey(new JwtProperties("", true)).getEncoded())
                .hasSize(32);
    }

    @Test
    void 설정값을_문자열로_바꿔도_키가_나오지_않는다() {
        String key =
                Base64.getEncoder().encodeToString("x".repeat(32).getBytes(StandardCharsets.UTF_8));

        assertThat(new JwtProperties(key, false).toString()).doesNotContain(key);
    }
}
