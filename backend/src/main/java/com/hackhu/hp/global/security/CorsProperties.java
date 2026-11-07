package com.hackhu.hp.global.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS로 허용할 프론트엔드 주소 ({@code app.cors.allowed-origins}).
 *
 * <p>값이 없으면 어떤 다른 출처도 허용하지 않는다. {@code *}는 쿠키를 싣는 요청에서 쓸 수 없고 모든 사이트를 허용하게 되므로 받지 않는다 (5-1-5).
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (allowedOrigins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalArgumentException("app.cors.allowed-origins에 *를 쓸 수 없습니다.");
        }
    }
}
