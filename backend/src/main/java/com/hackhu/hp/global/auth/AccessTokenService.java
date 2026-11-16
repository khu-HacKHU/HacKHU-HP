package com.hackhu.hp.global.auth;

import com.hackhu.hp.global.auth.InvalidAccessTokenException.Reason;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * access 토큰(JWT) 발급과 검증 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-4, 5-1-5).
 *
 * <ul>
 *   <li>알고리즘은 {@code HS256}으로 고정한다. 헤더의 {@code alg}를 따르지 않는다. {@code none}과 다른 알고리즘은 거부한다
 *       (CWE-347).
 *   <li>클레임은 {@code iss}, {@code sub}, {@code sid}, {@code iat}, {@code exp}만 넣는다. 역할, 이메일, 이름을 넣지
 *       않는다.
 *   <li>검증 키는 하나만 둔다. 키를 바꾸면 이전 토큰은 401을 받고 프론트엔드가 refresh로 새 토큰을 받는다 (DECISIONS.md A13).
 * </ul>
 *
 * <p>이 클래스는 서명과 만료만 본다. 로그인 세션이 살아 있는지는 {@link AccessTokenAuthenticator}가 DB에서 확인한다.
 */
@Slf4j
public class AccessTokenService {

    public static final Duration LIFETIME = Duration.ofMinutes(15);

    static final String ISSUER = "hackhu-hp";
    static final String SESSION_ID_CLAIM = "sid";
    static final int MIN_SECRET_BYTES = 32;

    private final Clock clock;
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;

    public AccessTokenService(JwtProperties properties, Clock clock) {
        this(signingKey(properties), clock);
    }

    AccessTokenService(SecretKey key, Clock clock) {
        this.clock = clock;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // 발급자와 만료는 verify에서 직접 확인한다. 만료와 다른 실패를 로그에서 나누기 위해서다.
        // 기본 검증기의 60초 허용 오차도 쓰지 않는다. 발급과 검증을 같은 서버가 하므로 시계 차이가 없다.
        this.decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
    }

    /** 사용자와 로그인 세션의 access 토큰을 만든다. 수명은 {@link #LIFETIME}이다. */
    public IssuedAccessToken issue(long userId, UUID sessionId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(LIFETIME);
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(ISSUER)
                        .subject(String.valueOf(userId))
                        .claim(SESSION_ID_CLAIM, sessionId.toString())
                        .issuedAt(now)
                        .expiresAt(expiresAt)
                        .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccessToken(value, LIFETIME.toSeconds());
    }

    /**
     * 서명, 발급자, 만료를 확인하고 사용자 ID와 로그인 세션 ID를 꺼낸다.
     *
     * @throws InvalidAccessTokenException 받아 줄 수 없는 토큰. 이유는 로그에만 쓴다
     */
    public AccessTokenClaims verify(String token) throws InvalidAccessTokenException {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException | IllegalArgumentException e) {
            // 클레임 변환 실패(예: exp가 숫자가 아님)도 받아 줄 수 없는 토큰이다. 500이 되지 않게 한다.
            throw new InvalidAccessTokenException(Reason.INVALID);
        }
        // getIssuer()는 iss를 URL로 바꾸려 해 "hackhu-hp" 같은 값에서 예외를 던진다. 문자열로 읽는다.
        if (!ISSUER.equals(jwt.getClaimAsString("iss"))) {
            throw new InvalidAccessTokenException(Reason.WRONG_ISSUER);
        }
        Instant expiresAt;
        try {
            expiresAt = jwt.getExpiresAt();
        } catch (RuntimeException e) {
            throw new InvalidAccessTokenException(Reason.BAD_CLAIMS);
        }
        if (expiresAt == null) {
            throw new InvalidAccessTokenException(Reason.BAD_CLAIMS);
        }
        if (!clock.instant().isBefore(expiresAt)) {
            throw new InvalidAccessTokenException(Reason.EXPIRED);
        }
        try {
            long userId = Long.parseLong(jwt.getSubject());
            UUID sessionId = UUID.fromString(jwt.getClaimAsString(SESSION_ID_CLAIM));
            return new AccessTokenClaims(userId, sessionId);
        } catch (RuntimeException e) {
            // sub가 숫자가 아니거나 sid가 UUID가 아니거나 없음
            throw new InvalidAccessTokenException(Reason.BAD_CLAIMS);
        }
    }

    // 키가 없거나 32바이트보다 짧으면 서버가 시작하지 않는다 (5-1-5, 6-7 I3).
    // 예외 메시지에 키 값을 넣지 않는다.
    static SecretKey signingKey(JwtProperties properties) {
        String secret = properties.secret();
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            if (!properties.allowGeneratedSecret()) {
                throw new IllegalStateException(
                        "access 토큰 서명 키(app.jwt.secret, 환경변수 JWT_SECRET)가 없습니다.");
            }
            log.warn("access 토큰 서명 키가 없어 이번 실행에서만 쓰는 무작위 키를 만듭니다. local과 테스트에서만 허용됩니다.");
            bytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
        } else {
            try {
                bytes = Base64.getDecoder().decode(secret.strip());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("access 토큰 서명 키가 Base64 형식이 아닙니다.");
            }
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "access 토큰 서명 키는 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * 발급한 access 토큰.
     *
     * @param value 토큰 원문. 로그에 남기지 않는다
     * @param expiresInSeconds 만료까지 남은 초 (응답의 {@code expiresIn})
     */
    public record IssuedAccessToken(String value, long expiresInSeconds) {

        @Override
        public String toString() {
            return "IssuedAccessToken[value=***, expiresInSeconds=" + expiresInSeconds + "]";
        }
    }
}
