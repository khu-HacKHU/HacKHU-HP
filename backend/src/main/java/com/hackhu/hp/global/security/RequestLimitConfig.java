package com.hackhu.hp.global.security;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 클라이언트 IP 판별과 요청 횟수 제한 카운터 (5-1-7). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClientIpProperties.class)
public class RequestLimitConfig {

    @Bean
    ClientIpResolver clientIpResolver(ClientIpProperties properties) {
        return new ClientIpResolver(properties);
    }

    @Bean
    RateLimiter rateLimiter(Clock clock) {
        return new RateLimiter(clock);
    }
}
