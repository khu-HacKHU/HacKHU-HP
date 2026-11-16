package com.hackhu.hp.global.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 클라이언트 IP를 정할 때 믿는 프록시 ({@code app.client-ip}).
 *
 * @param trustedProxies 믿는 프록시의 주소나 CIDR 대역. 운영에서는 로드밸런서 서브넷이다. 비어 있으면 {@code X-Forwarded-For}를 보지
 *     않는다
 * @param trustedHops 믿는 프록시 수. 로드밸런서만 있으면 1이다. {@code X-Forwarded-For}의 오른쪽에서 이 번째 값을 쓴다
 */
@ConfigurationProperties("app.client-ip")
public record ClientIpProperties(List<String> trustedProxies, Integer trustedHops) {

    public ClientIpProperties {
        trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
        trustedHops = trustedHops == null ? 1 : trustedHops;
        if (trustedHops < 1) {
            throw new IllegalArgumentException("app.client-ip.trusted-hops는 1 이상이어야 합니다.");
        }
    }
}
