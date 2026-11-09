package com.hackhu.hp.global.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * access 토큰 서명 키 ({@code app.jwt}).
 *
 * @param secret Base64로 인코딩한 32바이트 이상의 무작위 값. 운영에서는 환경변수 {@code JWT_SECRET}으로 주입하고 값은 파라미터 스토어에만
 *     둔다. 설정 파일에 적지 않는다
 * @param allowGeneratedSecret {@code secret}이 비었을 때 실행할 때마다 무작위 키를 만들지. local과 테스트에서만 켠다. 꺼져 있는데 키가
 *     없으면 서버가 시작하지 않는다
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, boolean allowGeneratedSecret) {

    // 값이 로그나 예외 메시지에 찍히지 않게 한다 (CWE-532).
    @Override
    public String toString() {
        return "JwtProperties[secret=***, allowGeneratedSecret=" + allowGeneratedSecret + "]";
    }
}
