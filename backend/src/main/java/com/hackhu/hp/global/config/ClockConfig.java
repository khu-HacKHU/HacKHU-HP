package com.hackhu.hp.global.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 서버 시계. 시각을 직접 {@code Instant.now()}로 읽지 않고 이 빈을 받으면 테스트에서 시간을 옮길 수 있다.
 *
 * <p>UTC로 고정한다. 시각은 UTC로만 다룬다 (backend/AGENTS.md "시각 컬럼").
 *
 * <p>마이크로초 단위로 끊는다. PostgreSQL의 {@code TIMESTAMPTZ}가 마이크로초까지만 저장하므로, 나노초가 남으면 저장했다 다시 읽은 만료 시각과
 * 1마이크로초 어긋나 경계에서 만료 판정이 달라진다.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
