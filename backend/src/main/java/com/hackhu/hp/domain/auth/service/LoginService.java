package com.hackhu.hp.domain.auth.service;

import static com.hackhu.hp.global.security.AuthRateLimits.LOGIN_ATTEMPTS_PER_IP;
import static com.hackhu.hp.global.security.AuthRateLimits.LOGIN_FAILURES_PER_IP_AND_EMAIL;

import com.hackhu.hp.domain.auth.entity.SessionRevokeReason;
import com.hackhu.hp.domain.auth.exception.AuthErrorCode;
import com.hackhu.hp.domain.auth.repository.LoginSessionRepository;
import com.hackhu.hp.domain.auth.repository.RefreshTokenRepository;
import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.domain.user.repository.UserRepository;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.error.TooManyRequestsException;
import com.hackhu.hp.global.security.RateLimiter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그인 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-4, 5-2-QUALITY-CONCURRENCY.md 5-2-3).
 *
 * <ol>
 *   <li>요청 횟수 제한을 확인한다. IP 기준은 모든 시도를, IP와 이메일 조합 기준은 실패만 센다 (5-1-7).
 *   <li>비밀번호를 bcrypt로 검증한다. 잠금과 트랜잭션 밖에서 한다. 이메일이 없어도 더미 해시와 비교해 응답 시간을 맞춘다 (CWE-208).
 *   <li>한 트랜잭션에서 검증한 해시가 아직 현재 값인지 다시 확인하고 세션을 만든다. 임시 비밀번호가 있는 계정은 항상 임시 비밀번호 경로로 간다.
 * </ol>
 *
 * <p>실패는 이유와 상관없이 같은 {@code 401 AUTH_INVALID_CREDENTIALS}다. 어느 쪽이 틀렸는지 알려 주지 않는다.
 */
@Slf4j
@Service
public class LoginService {

    // bcrypt는 비밀번호의 앞 72바이트만 쓴다. 넘으면 Spring Security가 예외를 던진다.
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final LoginSessionRepository loginSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SessionIssuer sessionIssuer;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;
    private final LoginFailureMonitor failureMonitor;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    // 가입되지 않은 이메일도 같은 bcrypt 계산을 거치게 하는 해시. 서버가 시작할 때 무작위 값으로 한 번 만든다.
    private final String dummyHash;

    LoginService(
            UserRepository userRepository,
            LoginSessionRepository loginSessionRepository,
            RefreshTokenRepository refreshTokenRepository,
            SessionIssuer sessionIssuer,
            PasswordEncoder passwordEncoder,
            RateLimiter rateLimiter,
            LoginFailureMonitor failureMonitor,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.userRepository = userRepository;
        this.loginSessionRepository = loginSessionRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.sessionIssuer = sessionIssuer;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.failureMonitor = failureMonitor;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        this.dummyHash = passwordEncoder.encode(Base64.getEncoder().encodeToString(random));
    }

    /**
     * @param email {@code Emails#normalize}로 정규화한 이메일
     * @param clientIp {@code ClientIpResolver}가 정한 클라이언트 IP
     * @param existingRefreshToken 요청에 실려 온 refresh 쿠키. 같은 브라우저에서 다시 로그인하면 그 세션을 폐기한다. 없으면 null
     * @throws TooManyRequestsException 요청 횟수 제한에 걸림
     * @throws BusinessException 로그인 실패. {@code AUTH_INVALID_CREDENTIALS}
     */
    public IssuedTokens login(
            String email, String password, String clientIp, String existingRefreshToken) {
        checkLimit(rateLimiter.tryAcquire(LOGIN_ATTEMPTS_PER_IP, clientIp), clientIp);
        // 실패만 세지만 시도 전에 한 번을 잡아 둔다. 동시에 들어온 시도가 모두 확인을 통과해 한도를 넘지 않게 하려는 것이다.
        // 성공하면 돌려준다. 가입되지 않은 이메일도 똑같이 센다 (5-1-7).
        String failureKey = clientIp + ' ' + email;
        checkLimit(rateLimiter.tryAcquire(LOGIN_FAILURES_PER_IP_AND_EMAIL, failureKey), clientIp);

        Optional<User> user = userRepository.findByEmail(email);
        String verifiedHash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordMatches(password, verifiedHash);

        Optional<IssuedTokens> issued =
                user.isPresent() && matches
                        ? transactionTemplate.execute(
                                status ->
                                        startSession(
                                                user.get(), verifiedHash, existingRefreshToken))
                        : Optional.empty();
        if (issued == null || issued.isEmpty()) {
            Long userId = user.map(User::getId).orElse(null);
            // 입력한 이메일은 남기지 않는다. 비밀번호를 이메일 칸에 잘못 넣는 경우가 있다 (5-1-9, CWE-532).
            log.info("로그인 실패: ip={}, userId={}", clientIp, userId);
            if (userId != null) {
                failureMonitor.recordFailure(userId, clientIp);
            }
            throw new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        rateLimiter.release(LOGIN_FAILURES_PER_IP_AND_EMAIL, failureKey);
        log.info("로그인: userId={}", issued.get().body().user().id());
        return issued.get();
    }

    // 검증한 해시가 아직 현재 값일 때만 세션을 만든다. 아니면 비어 있음 (5-2-3).
    private Optional<IssuedTokens> startSession(
            User user, String verifiedHash, String existingRefreshToken) {
        long userId = user.getId();
        boolean stillValid =
                user.getTempPasswordExpiresAt() != null
                        // 임시 비밀번호 경로. 만료, 이미 씀, 그 사이 재발급이면 0행이다.
                        ? userRepository.markTemporaryPasswordUsed(userId, verifiedHash) == 1
                        // 일반 경로. 행을 잠그고 해시를 다시 읽는다. 그 사이 임시 비밀번호가 발급되었어도 해시가 달라 실패한다.
                        : userRepository
                                .findPasswordHashForUpdate(userId)
                                .filter(verifiedHash::equals)
                                .isPresent();
        if (!stillValid) {
            return Optional.empty();
        }
        // 응답의 mustChangePassword 등은 잠근 뒤의 값으로 준다.
        User current = userRepository.findById(userId).orElseThrow();
        revokeExistingSession(existingRefreshToken);
        return Optional.of(sessionIssuer.startSession(current));
    }

    // 같은 브라우저에서 다시 로그인하면 이전 쿠키의 세션을 폐기한다. 쿠키를 덮어써 주인 없는 세션이 30일 남는 것을 막는다 (4-2-4).
    private void revokeExistingSession(String existingRefreshToken) {
        if (existingRefreshToken == null || existingRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository
                .findSessionIdByTokenHash(RefreshTokenValues.hash(existingRefreshToken))
                .ifPresent(
                        sessionId ->
                                loginSessionRepository.revoke(
                                        sessionId, SessionRevokeReason.LOGOUT, clock.instant()));
    }

    // 72바이트가 넘는 비밀번호는 가입 규칙상 저장될 수 없으므로 틀린 것으로 본다.
    // 응답 시간을 맞추려고 bcrypt 계산은 그대로 한다.
    private boolean passwordMatches(String rawPassword, String hash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            // 빈 문자열은 bcrypt 계산 없이 false가 될 수 있어 아무 값이나 넣는다.
            passwordEncoder.matches("timing-placeholder", hash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    private static void checkLimit(RateLimiter.Decision decision, String clientIp) {
        if (decision.allowed()) {
            return;
        }
        // 창마다 처음 거부할 때만 남긴다 (CWE-779).
        if (decision.firstRejection()) {
            log.info("로그인 요청 제한: ip={}", clientIp);
        }
        throw new TooManyRequestsException(decision.retryAfter());
    }
}
