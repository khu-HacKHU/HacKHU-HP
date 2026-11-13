package com.hackhu.hp.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static final String PROXY = "10.0.1.5";

    private final ClientIpResolver resolver =
            new ClientIpResolver(new ClientIpProperties(List.of("10.0.1.0/24"), 1));

    private static MockHttpServletRequest request(String remoteAddr, String... forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        for (String value : forwardedFor) {
            request.addHeader("X-Forwarded-For", value);
        }
        return request;
    }

    @Test
    void 믿는_프록시가_아니면_X_Forwarded_For를_무시한다_L5() {
        assertThat(resolver.resolve(request("203.0.113.7", "198.51.100.1")))
                .isEqualTo("203.0.113.7");
    }

    @Test
    void 믿는_프록시에서_오면_오른쪽_값을_쓴다() {
        assertThat(resolver.resolve(request(PROXY, "198.51.100.1"))).isEqualTo("198.51.100.1");
    }

    @Test
    void 클라이언트가_앞에_붙인_위조_값은_무시한다_L4() {
        assertThat(resolver.resolve(request(PROXY, "1.2.3.4, 5.6.7.8, 198.51.100.1")))
                .isEqualTo("198.51.100.1");
        assertThat(resolver.resolve(request(PROXY, "9.9.9.9", "198.51.100.1")))
                .isEqualTo("198.51.100.1");
    }

    @Test
    void 신뢰_홉이_둘이면_오른쪽에서_두_번째_값을_쓴다() {
        ClientIpResolver twoHops =
                new ClientIpResolver(new ClientIpProperties(List.of("10.0.1.0/24"), 2));

        assertThat(twoHops.resolve(request(PROXY, "1.2.3.4, 198.51.100.1, 10.0.1.9")))
                .isEqualTo("198.51.100.1");
    }

    @Test
    void 헤더가_없거나_모자라거나_IP가_아니면_연결_상대의_주소를_쓴다() {
        ClientIpResolver twoHops =
                new ClientIpResolver(new ClientIpProperties(List.of("10.0.1.0/24"), 2));

        assertThat(resolver.resolve(request(PROXY))).isEqualTo(PROXY);
        assertThat(twoHops.resolve(request(PROXY, "198.51.100.1"))).isEqualTo(PROXY);
        assertThat(resolver.resolve(request(PROXY, "evil.example.com"))).isEqualTo(PROXY);
        assertThat(resolver.resolve(request(PROXY, "1.2.3.4, "))).isEqualTo(PROXY);
        assertThat(resolver.resolve(request(PROXY, "256.1.1.1"))).isEqualTo(PROXY);
        assertThat(resolver.resolve(request(PROXY, "fe80::1%eth0"))).isEqualTo(PROXY);
    }

    @Test
    void IPv6는_표준_표기로_바꾼다() {
        assertThat(resolver.resolve(request(PROXY, "2001:DB8:0:0:0:0:0:1")))
                .isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void 프록시를_설정하지_않으면_항상_연결_상대의_주소를_쓴다() {
        ClientIpResolver none = new ClientIpResolver(new ClientIpProperties(null, null));

        assertThat(none.resolve(request("127.0.0.1", "198.51.100.1"))).isEqualTo("127.0.0.1");
    }

    @Test
    void 잘못된_대역이나_홉_수는_시작할_때_거부한다() {
        assertThatThrownBy(
                        () ->
                                new ClientIpResolver(
                                        new ClientIpProperties(List.of("not-a-cidr/99"), 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClientIpProperties(List.of(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void IP_형식이_아닌_연결_주소도_로그에_쓸_수_있는_값으로_바꾼다() {
        assertThat(resolver.resolve(request("1.2.3.4\nINFO 위조된 로그 줄")))
                .doesNotContain("\n")
                .matches("[0-9A-Fa-f:._]{1,45}");
        assertThat(resolver.resolve(request("/var/run/app.sock"))).doesNotContain("/");
        assertThat(ClientIpResolver.forKeyAndLog(null)).isEqualTo("unknown");
    }

    @Test
    void IP_형식은_그대로_둔다() {
        assertThat(ClientIpResolver.forKeyAndLog("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(ClientIpResolver.forKeyAndLog("2001:db8:0:0:0:0:0:1"))
                .isEqualTo("2001:db8:0:0:0:0:0:1");
    }
}
