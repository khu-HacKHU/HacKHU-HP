package com.hackhu.hp.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackhu.hp.support.MutableClock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final RateLimitRule RULE = new RateLimitRule("test", 3, Duration.ofMinutes(5));

    private final MutableClock clock = new MutableClock();
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void 한도까지_허용하고_다음_요청은_남은_시간과_함께_거부한다() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(RULE, "a").allowed()).isTrue();
        }
        clock.advance(Duration.ofMinutes(1));

        RateLimiter.Decision rejected = limiter.tryAcquire(RULE, "a");

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfter()).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void 처음_거부한_요청만_firstRejection이다() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(RULE, "a");
        }

        assertThat(limiter.tryAcquire(RULE, "a").firstRejection()).isTrue();
        assertThat(limiter.tryAcquire(RULE, "a").firstRejection()).isFalse();
        assertThat(limiter.tryAcquire(RULE, "a").firstRejection()).isFalse();
    }

    @Test
    void 창이_지나면_다시_센다() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(RULE, "a");
        }
        assertThat(limiter.tryAcquire(RULE, "a").allowed()).isFalse();

        clock.advance(Duration.ofMinutes(5));

        assertThat(limiter.tryAcquire(RULE, "a").allowed()).isTrue();
    }

    @Test
    void release는_잡은_한_번을_돌려준다() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(RULE, "a");
        }
        limiter.release(RULE, "a");

        assertThat(limiter.tryAcquire(RULE, "a").allowed()).isTrue();
        assertThat(limiter.tryAcquire(RULE, "a").allowed()).isFalse();
    }

    @Test
    void 창이_바뀐_뒤의_release는_새_창을_줄이지_않는다() {
        limiter.tryAcquire(RULE, "a");
        clock.advance(Duration.ofMinutes(5));

        limiter.release(RULE, "a");
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire(RULE, "a").allowed()).isTrue();
        }
        assertThat(limiter.tryAcquire(RULE, "a").allowed()).isFalse();
    }

    @Test
    void 키와_규칙마다_따로_센다() {
        RateLimitRule other = new RateLimitRule("other", 3, Duration.ofMinutes(5));
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire(RULE, "a");
        }

        assertThat(limiter.tryAcquire(RULE, "b").allowed()).isTrue();
        assertThat(limiter.tryAcquire(other, "a").allowed()).isTrue();
    }

    @Test
    void 동시에_요청해도_한도를_넘겨_허용하지_않는다() throws Exception {
        RateLimitRule rule = new RateLimitRule("concurrent", 10, Duration.ofMinutes(5));
        int threads = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    if (limiter.tryAcquire(rule, "same").allowed()) {
                                        allowed.incrementAndGet();
                                    }
                                    return null;
                                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(allowed.get()).isEqualTo(10);
    }

    @Test
    void 항목_수는_상한을_넘지_않는다() {
        for (int i = 0; i < RateLimiter.MAX_ENTRIES + 1_000; i++) {
            limiter.tryAcquire(RULE, "key-" + i);
        }

        assertThat(limiter.size()).isLessThanOrEqualTo(RateLimiter.MAX_ENTRIES);
    }

    @Test
    void 인증권한_규칙은_명세의_값과_같다() {
        assertThat(AuthRateLimits.LOGIN_FAILURES_PER_IP_AND_EMAIL)
                .isEqualTo(new RateLimitRule("login-ip-email", 10, Duration.ofMinutes(5)));
        assertThat(AuthRateLimits.LOGIN_ATTEMPTS_PER_IP)
                .isEqualTo(new RateLimitRule("login-ip", 200, Duration.ofMinutes(5)));
        assertThat(AuthRateLimits.SIGNUP_ATTEMPTS_PER_IP)
                .isEqualTo(new RateLimitRule("signup-ip", 50, Duration.ofHours(1)));
        assertThat(AuthRateLimits.PASSWORD_CONFIRM_FAILURES_PER_USER)
                .isEqualTo(new RateLimitRule("password-confirm-user", 10, Duration.ofMinutes(5)));
    }
}
