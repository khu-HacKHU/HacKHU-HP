package com.hackhu.hp.domain.auth.service;

import com.hackhu.hp.domain.auth.repository.LoginSessionRepository;
import com.hackhu.hp.domain.auth.repository.SessionPrincipal;
import com.hackhu.hp.global.auth.AccessTokenAuthenticator;
import com.hackhu.hp.global.auth.AccessTokenClaims;
import com.hackhu.hp.global.auth.AccessTokenService;
import com.hackhu.hp.global.auth.InvalidAccessTokenException;
import com.hackhu.hp.global.auth.LoginUser;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 요청마다 하는 인증 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-4 2~6단계). access 토큰 검증 필터가 부른다.
 *
 * <p>역할, 계정 상태, 세션 폐기 여부를 요청마다 DB에서 읽는다. 역할 변경과 탈퇴, 로그아웃이 다음 요청부터 반영된다 (DECISIONS.md A2).
 *
 * <p>거부 이유는 응답에 드러내지 않고 DEBUG 로그에만 남긴다 (A14). 서명이 틀린 토큰은 누구나 만들 수 있어 높은 수준으로 남기면 로그를 쌓을 수 있다
 * (CWE-779).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginSessionAuthenticator implements AccessTokenAuthenticator {

    private final AccessTokenService accessTokenService;
    private final LoginSessionRepository loginSessionRepository;
    private final Clock clock;

    @Override
    public Optional<AuthenticatedSession> authenticate(String accessToken) {
        AccessTokenClaims claims;
        try {
            claims = accessTokenService.verify(accessToken);
        } catch (InvalidAccessTokenException e) {
            log.debug("access 토큰 거부: reason={}", e.reason());
            return Optional.empty();
        }

        Optional<SessionPrincipal> found = loginSessionRepository.findPrincipal(claims.sessionId());
        // Optional.map은 null을 빈 값으로 바꾸므로 거부 이유를 map으로 꺼내지 않는다. 받아 준 세션까지 거부하게 된다.
        String rejection =
                found.isEmpty()
                        ? "SESSION_NOT_FOUND"
                        : rejection(found.get(), claims, clock.instant());
        if (rejection != null) {
            log.debug("access 토큰 거부: reason={}, userId={}", rejection, claims.userId());
            return Optional.empty();
        }
        SessionPrincipal principal = found.get();
        return Optional.of(
                new AuthenticatedSession(
                        new LoginUser(principal.userId(), principal.role()),
                        principal.mustChangePassword()));
    }

    // 받아 줄 수 없으면 이유를, 받아 주면 null을 돌려준다.
    private static String rejection(SessionPrincipal p, AccessTokenClaims claims, Instant now) {
        if (p.sessionRevokedAt() != null) {
            return "SESSION_REVOKED";
        }
        if (!now.isBefore(p.sessionExpiresAt())) {
            return "SESSION_EXPIRED";
        }
        // 세션의 사용자가 sub와 다르면 거부한다. 서명 키가 유출되어도 남의 세션 ID로 다른 사람 행세를 하지 못하게 한다.
        if (p.userId() != claims.userId()) {
            return "USER_MISMATCH";
        }
        if (p.withdrawnAt() != null) {
            return "WITHDRAWN";
        }
        return null;
    }
}
