package com.hackhu.hp.domain.auth.service;

import com.hackhu.hp.domain.auth.entity.LoginSession;
import com.hackhu.hp.domain.auth.entity.RefreshToken;
import com.hackhu.hp.domain.auth.entity.SessionRevokeReason;
import com.hackhu.hp.domain.auth.repository.LoginSessionRepository;
import com.hackhu.hp.domain.auth.repository.RefreshTokenRepository;
import com.hackhu.hp.domain.auth.service.SessionIssuer.CreatedRefreshToken;
import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * access 토큰 갱신과 refresh 토큰 회전 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-5).
 *
 * <p>같은 세션의 refresh는 {@code login_sessions} 행을 잠가 하나씩 처리한다. 받은 토큰을 아래 순서로 보고 처음 맞는 줄로 처리한다.
 *
 * <ol>
 *   <li>현재 토큰: 회전하고 새 쿠키를 준다.
 *   <li>회전한 지 10초 안인 토큰(몇 단계 앞이든): 다른 탭이 먼저 회전한 동시 요청이다. access 토큰만 주고 쿠키는 보내지 않는다.
 *   <li>현재 토큰의 바로 앞 토큰이고 10초가 지남: 응답 유실이다. 현재 토큰은 한 번도 쓰이지 않았으므로 폐기하고 새 토큰을 준다.
 *   <li>그 밖의 예전 토큰, 응답 유실 복구로 폐기된 토큰: 재사용이다. 탈취로 보고 세션을 폐기한다.
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRefreshService {

    static final Duration CONCURRENT_REFRESH_WINDOW = Duration.ofSeconds(10);

    private final LoginSessionRepository loginSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SessionIssuer sessionIssuer;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /**
     * @param refreshToken 요청의 refresh 쿠키. 없으면 null
     * @param clientIp 재사용을 감지했을 때 로그에 남긴다 (5-1-10)
     * @return 받아 주지 못하면 비어 있음. 컨트롤러가 401과 쿠키 삭제를 준다. 재사용으로 세션을 폐기한 것은 커밋된다
     */
    public Optional<IssuedTokens> refresh(String refreshToken, String clientIp) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = RefreshTokenValues.hash(refreshToken);
        return transactionTemplate.execute(status -> refreshLocked(tokenHash, clientIp));
    }

    private Optional<IssuedTokens> refreshLocked(String tokenHash, String clientIp) {
        // 세션을 먼저 잠그고 나서 토큰을 읽는다. 잠그기 전에 읽은 토큰 상태는 이미 다른 요청이 바꿨을 수 있다.
        Optional<UUID> sessionId = refreshTokenRepository.findSessionIdByTokenHash(tokenHash);
        if (sessionId.isEmpty()) {
            return Optional.empty();
        }
        Optional<LoginSession> locked = loginSessionRepository.findByIdForUpdate(sessionId.get());
        Instant now = clock.instant();
        if (locked.isEmpty() || !locked.get().isActive(now)) {
            return Optional.empty();
        }
        LoginSession session = locked.get();
        Optional<User> user =
                userRepository
                        .findById(session.getUserId())
                        .filter(found -> found.getWithdrawnAt() == null);
        Optional<RefreshToken> received = refreshTokenRepository.findByTokenHash(tokenHash);
        if (user.isEmpty() || received.isEmpty() || received.get().isExpired(now)) {
            return Optional.empty();
        }
        RefreshToken token = received.get();

        if (token.isCurrent()) {
            return Optional.of(rotate(token, session, user.get(), now));
        }
        if (token.wasReplacedWithin(CONCURRENT_REFRESH_WINDOW, now)) {
            // 먼저 처리된 응답의 쿠키가 브라우저에 남는다.
            return Optional.of(
                    new IssuedTokens(sessionIssuer.accessTokenFor(user.get(), session), null));
        }
        Optional<RefreshToken> current = refreshTokenRepository.findCurrent(session.getId());
        if (current.isPresent() && token.isPredecessorOf(current.get())) {
            return Optional.of(recoverLostResponse(token, current.get(), session, user.get(), now));
        }

        loginSessionRepository.revoke(session.getId(), SessionRevokeReason.REUSE_DETECTED, now);
        log.warn(
                "refresh 토큰 재사용 감지, 세션 폐기: userId={}, sessionId={}, ip={}",
                session.getUserId(),
                session.getId(),
                clientIp);
        return Optional.empty();
    }

    private IssuedTokens rotate(RefreshToken token, LoginSession session, User user, Instant now) {
        // 앞 토큰을 먼저 회전 처리해야 새 토큰이 현재 토큰 하나 규칙(uk_refresh_tokens_current)에 걸리지 않는다.
        token.markReplaced(now);
        refreshTokenRepository.flush();
        CreatedRefreshToken next = sessionIssuer.issueRefreshToken(session, now);
        token.linkReplacement(next.entity());
        return new IssuedTokens(sessionIssuer.accessTokenFor(user, session), next.toCookie(now));
    }

    // 클라이언트가 현재 토큰을 받지 못했다. 현재 토큰을 폐기하고 받은 토큰의 다음 토큰을 새로 준다.
    private IssuedTokens recoverLostResponse(
            RefreshToken received,
            RefreshToken current,
            LoginSession session,
            User user,
            Instant now) {
        current.revoke(now);
        refreshTokenRepository.flush();
        CreatedRefreshToken next = sessionIssuer.issueRefreshToken(session, now);
        received.markReplaced(now);
        received.linkReplacement(next.entity());
        log.info("refresh 응답 유실 복구: userId={}, sessionId={}", user.getId(), session.getId());
        return new IssuedTokens(sessionIssuer.accessTokenFor(user, session), next.toCookie(now));
    }
}
