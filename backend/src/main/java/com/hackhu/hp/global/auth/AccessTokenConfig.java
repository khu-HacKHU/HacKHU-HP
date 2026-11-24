package com.hackhu.hp.global.auth;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** access 토큰 서명 키와 발급, 검증. 키가 없거나 짧으면 이 빈을 만들지 못해 서버가 시작하지 않는다 (6-7 I3). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class AccessTokenConfig {

    @Bean
    AccessTokenService accessTokenService(JwtProperties properties, Clock clock) {
        return new AccessTokenService(properties, clock);
    }
}
