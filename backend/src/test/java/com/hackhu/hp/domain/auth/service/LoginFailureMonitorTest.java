package com.hackhu.hp.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackhu.hp.support.MutableClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** 한 계정에 몰린 로그인 실패 감지 (5-1-7, 5-1-10). */
class LoginFailureMonitorTest {

    private final MutableClock clock = new MutableClock();
    private final LoginFailureMonitor monitor = new LoginFailureMonitor(clock);

    @Test
    void 쉰한_번째_실패에서_한_번만_경고한다() {
        for (int i = 0; i < 50; i++) {
            assertThat(monitor.recordFailure(1L, "192.0.2." + (i % 6))).isFalse();
        }

        assertThat(monitor.recordFailure(1L, "192.0.2.1")).isTrue();
        assertThat(monitor.recordFailure(1L, "192.0.2.1")).isFalse();
    }

    @Test
    void 창이_지나면_다시_센다() {
        for (int i = 0; i < 51; i++) {
            monitor.recordFailure(1L, "192.0.2.1");
        }
        clock.advance(LoginFailureMonitor.WINDOW);

        for (int i = 0; i < 50; i++) {
            assertThat(monitor.recordFailure(1L, "192.0.2.1")).isFalse();
        }
        assertThat(monitor.recordFailure(1L, "192.0.2.1")).isTrue();
    }

    @Test
    void 계정마다_따로_센다() {
        for (int i = 0; i < 50; i++) {
            monitor.recordFailure(1L, "192.0.2.1");
        }

        assertThat(monitor.recordFailure(2L, "192.0.2.1")).isFalse();
    }

    // 크리덴셜 스터핑은 여러 IP에서 한꺼번에 들어온다. 51번째와 52번째가 겹쳐도 경고는 정확히 한 번이다.
    @RepeatedTest(20)
    void 같은_계정으로_동시에_실패해도_경고는_정확히_한_번이다() throws Exception {
        int threads = 60;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger alerts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String ip = "192.0.2." + i;
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    if (monitor.recordFailure(7L, ip)) {
                                        alerts.incrementAndGet();
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

        assertThat(alerts.get()).isEqualTo(1);
    }
}
