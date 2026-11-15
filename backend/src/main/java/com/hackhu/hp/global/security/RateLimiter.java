package com.hackhu.hp.global.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 고정 창 요청 횟수 제한 카운터 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-7).
 *
 * <p>카운터는 서버 메모리에 둔다. 인스턴스가 하나라 분산 카운터가 필요 없다. 재배포하면 초기화되고, 인스턴스를 늘리면 한도가 인스턴스 수만큼 느슨해진다. 그때 Redis나
 * WAF로 옮긴다 (DECISIONS.md A11).
 *
 * <p>창이 끝나면 저절로 지워지는 캐시에 두고 항목 수를 {@value #MAX_ENTRIES}개로 묶는다. 무작위 이메일로 요청해 메모리를 채우는 것을 막는다. 상한에
 * 닿으면 오래 쓰지 않은 카운터부터 밀려난다.
 *
 * <p>늘리고 확인하는 일은 키마다 원자적으로 한다. 동시 요청이 와도 한도를 넘겨 통과시키지 않는다 (5-2-5).
 */
public class RateLimiter {

    static final long MAX_ENTRIES = 100_000;

    private final Clock clock;
    private final Cache<String, Window> windows;

    public RateLimiter(Clock clock) {
        this.clock = clock;
        this.windows =
                Caffeine.newBuilder()
                        .maximumSize(MAX_ENTRIES)
                        .expireAfter(new WindowExpiry(clock))
                        .build();
    }

    /**
     * 한도 안이면 한 번을 세고 허용한다. 한도에 닿았으면 세지 않고 거부한다.
     *
     * <p>실패만 세는 규칙은 시도 전에 이 메서드로 한 번을 잡아 두고, 성공하면 {@link #release}로 돌려준다. 시도한 뒤에 세면 동시에 들어온 시도가 모두
     * 확인을 통과해 한도를 넘는다.
     */
    public Decision tryAcquire(RateLimitRule rule, String key) {
        Instant now = clock.instant();
        Decision[] result = new Decision[1];
        windows.asMap()
                .compute(
                        cacheKey(rule, key),
                        (k, current) -> {
                            Window window =
                                    current == null || current.isOver(now)
                                            ? Window.start(now, rule.window())
                                            : current;
                            if (window.count() < rule.limit()) {
                                result[0] = Decision.allow();
                                return window.withCount(window.count() + 1);
                            }
                            result[0] =
                                    Decision.reject(
                                            Duration.between(now, window.end()),
                                            !window.rejected());
                            return window.markRejected();
                        });
        return result[0];
    }

    /** {@link #tryAcquire}로 잡은 한 번을 돌려준다. 그 사이 창이 바뀌었으면 아무것도 하지 않는다. */
    public void release(RateLimitRule rule, String key) {
        Instant now = clock.instant();
        windows.asMap()
                .computeIfPresent(
                        cacheKey(rule, key),
                        (k, current) ->
                                current.isOver(now) || current.count() == 0
                                        ? current
                                        : current.withCount(current.count() - 1));
    }

    long size() {
        windows.cleanUp();
        return windows.estimatedSize();
    }

    // 규칙 이름과 키 사이에 NUL을 둔다. 키(이메일 등)에 들어올 수 없는 문자라 다른 규칙의 키와 겹치지 않는다.
    private static String cacheKey(RateLimitRule rule, String key) {
        return rule.name() + '\u0000' + key;
    }

    /**
     * 허용 여부.
     *
     * @param retryAfter 거부했을 때 창이 끝날 때까지 남은 시간
     * @param firstRejection 이 창에서 처음 거부한 요청인지. 거부 로그를 창마다 한 번만 남기는 데 쓴다. 거부할 때마다 남기면 누구나 로그를 쌓을 수
     *     있다 (CWE-779)
     */
    public record Decision(boolean allowed, Duration retryAfter, boolean firstRejection) {

        static Decision allow() {
            return new Decision(true, Duration.ZERO, false);
        }

        static Decision reject(Duration retryAfter, boolean firstRejection) {
            return new Decision(false, retryAfter, firstRejection);
        }
    }

    private record Window(Instant start, Duration length, int count, boolean rejected) {

        static Window start(Instant now, Duration length) {
            return new Window(now, length, 0, false);
        }

        Instant end() {
            return start.plus(length);
        }

        boolean isOver(Instant now) {
            return !now.isBefore(end());
        }

        Window withCount(int newCount) {
            return new Window(start, length, newCount, rejected);
        }

        Window markRejected() {
            return new Window(start, length, count, true);
        }
    }

    // 창이 끝나면 캐시에서 지운다. 창의 끝은 서버 시계로 정하므로, 캐시가 아직 지우지 않았어도 tryAcquire가 끝난 창을 새로 시작한다.
    private record WindowExpiry(Clock clock) implements Expiry<String, Window> {

        @Override
        public long expireAfterCreate(String key, Window value, long currentTime) {
            return nanosUntilEnd(value);
        }

        @Override
        public long expireAfterUpdate(
                String key, Window value, long currentTime, long currentDuration) {
            return nanosUntilEnd(value);
        }

        @Override
        public long expireAfterRead(
                String key, Window value, long currentTime, long currentDuration) {
            return currentDuration;
        }

        private long nanosUntilEnd(Window value) {
            Duration remaining = Duration.between(clock.instant(), value.end());
            return remaining.isNegative() ? 0 : remaining.toNanos();
        }
    }
}
