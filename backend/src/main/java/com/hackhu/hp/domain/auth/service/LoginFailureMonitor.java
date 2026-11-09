package com.hackhu.hp.domain.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 한 계정에 여러 IP에서 들어오는 로그인 실패를 감지한다 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-7, 5-1-10).
 *
 * <p>막지 않고 경고 로그만 남긴다. 이메일만을 기준으로 막으면 공격자가 남의 이메일로 일부러 실패해 그 사람이 어디서도 로그인하지 못하게 만들 수 있다
 * (DECISIONS.md A11). 운영진 알림 방법은 관측 문서(5-4)에서 정한다.
 *
 * <p>가입된 계정의 실패만 센다. 무작위 이메일로 메모리를 채울 수 없다.
 */
@Slf4j
@Component
class LoginFailureMonitor {

    static final int ALERT_THRESHOLD = 50;
    static final Duration WINDOW = Duration.ofHours(1);
    private static final long MAX_ACCOUNTS = 100_000;
    // 서로 다른 IP 수는 알림에 참고로만 쓰므로 이만큼까지만 기억한다.
    private static final int MAX_TRACKED_IPS = 1_000;

    private final Clock clock;
    private final Cache<Long, Failures> failures;

    LoginFailureMonitor(Clock clock) {
        this.clock = clock;
        this.failures =
                Caffeine.newBuilder().maximumSize(MAX_ACCOUNTS).expireAfterWrite(WINDOW).build();
    }

    void recordFailure(long userId, String clientIp) {
        Instant now = clock.instant();
        Failures updated =
                failures.asMap()
                        .compute(
                                userId,
                                (id, current) -> {
                                    Failures window =
                                            current == null || current.isOver(now)
                                                    ? new Failures(now)
                                                    : current;
                                    window.add(clientIp);
                                    return window;
                                });
        // 넘는 순간 한 번만 남긴다. 실패할 때마다 남기면 누구나 경고 로그를 쌓을 수 있다 (CWE-779).
        if (updated.count() == ALERT_THRESHOLD + 1) {
            log.warn(
                    "한 계정에 로그인 실패가 많음: userId={}, failures={}, distinctIps={}",
                    userId,
                    updated.count(),
                    updated.distinctIps());
        }
    }

    // compute 안에서만 바꾼다. 같은 키의 compute는 한 번에 하나씩 돈다.
    private static final class Failures {

        private final Instant start;
        private final Set<String> ips = new HashSet<>();
        private int count;

        Failures(Instant start) {
            this.start = start;
        }

        boolean isOver(Instant now) {
            return !now.isBefore(start.plus(WINDOW));
        }

        void add(String ip) {
            count++;
            if (ips.size() < MAX_TRACKED_IPS) {
                ips.add(ip);
            }
        }

        int count() {
            return count;
        }

        int distinctIps() {
            return ips.size();
        }
    }
}
