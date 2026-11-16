package com.hackhu.hp.domain.auth.service;

import com.hackhu.hp.domain.auth.dto.TokenResponse;
import com.hackhu.hp.domain.auth.entity.LoginSession;
import com.hackhu.hp.domain.auth.entity.RefreshToken;
import com.hackhu.hp.domain.auth.repository.LoginSessionRepository;
import com.hackhu.hp.domain.auth.repository.RefreshTokenRepository;
import com.hackhu.hp.domain.auth.service.IssuedTokens.NewRefreshToken;
import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.global.auth.AccessTokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 세션, refresh 토큰, access 토큰을 만든다. 로그인과 refresh가 같이 쓴다.
 *
 * <p>모든 메서드는 부르는 쪽의 트랜잭션 안에서 돈다. 로그인은 {@code users} 행을, refresh는 {@code login_sessions} 행을 먼저 잠근 뒤
 * 부른다 (5-2-1 잠금 순서).
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
class SessionIssuer {

    private final LoginSessionRepository loginSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenService accessTokenService;
    private final Clock clock;

    /** 새 로그인 세션과 그 첫 refresh 토큰, access 토큰을 만든다. */
    IssuedTokens startSession(User user) {
        Instant now = clock.instant();
        LoginSession session = loginSessionRepository.save(LoginSession.start(user.getId(), now));
        CreatedRefreshToken refresh = issueRefreshToken(session, now);
        return new IssuedTokens(accessTokenFor(user, session), refresh.toCookie(now));
    }

    /** 세션에 새 refresh 토큰을 저장한다. 회전하는 쪽이 앞 토큰과 잇는다. */
    CreatedRefreshToken issueRefreshToken(LoginSession session, Instant now) {
        String value = RefreshTokenValues.generate();
        RefreshToken token =
                refreshTokenRepository.saveAndFlush(
                        RefreshToken.issue(session, RefreshTokenValues.hash(value), now));
        return new CreatedRefreshToken(token, value);
    }

    TokenResponse accessTokenFor(User user, LoginSession session) {
        return TokenResponse.of(accessTokenService.issue(user.getId(), session.getId()), user);
    }

    /** 저장한 refresh 토큰과 그 원문. */
    record CreatedRefreshToken(RefreshToken entity, String value) {

        NewRefreshToken toCookie(Instant now) {
            return new NewRefreshToken(value, Duration.between(now, entity.getExpiresAt()));
        }

        @Override
        public String toString() {
            return "CreatedRefreshToken[id=" + entity.getId() + ", value=***]";
        }
    }
}
