package com.hackhu.hp.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.global.auth.Role;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;

/** 로그인 요청 횟수 제한 (5-1-7, 6-RELEASE-TEST-CASES.md 6-5). */
@ExtendWith(OutputCaptureExtension.class)
class LoginRateLimitIntegrationTest extends AuthIntegrationTestSupport {

    private static final String WRONG = "wrong-password-123";

    private void fail(String email, String clientIp, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            login(email, WRONG, clientIp, null).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void 한_IP에서_한_이메일로_10번_실패하면_11번째는_맞는_비밀번호여도_429_L1() throws Exception {
        createUser("member@example.com", Role.MEMBER);
        fail("member@example.com", ip, 10);

        login("member@example.com", PASSWORD, ip, null)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "300"));
        // 막힌 요청은 비밀번호를 확인하지 않는다.
        assertThat(count("SELECT count(*) FROM login_sessions")).isZero();

        clock.advance(Duration.ofMinutes(5));
        login("member@example.com", PASSWORD, ip, null).andExpect(status().isOk());
    }

    @Test
    void 막힌_뒤에도_다른_IP에서는_로그인된다_L2() throws Exception {
        createUser("member@example.com", Role.MEMBER);
        fail("member@example.com", ip, 10);
        login("member@example.com", PASSWORD, ip, null).andExpect(status().isTooManyRequests());

        login("member@example.com", PASSWORD, randomIp(), null).andExpect(status().isOk());
    }

    @Test
    void 가입되지_않은_이메일도_똑같이_센다_L6() throws Exception {
        fail("nobody@example.com", ip, 10);

        login("nobody@example.com", WRONG, ip, null).andExpect(status().isTooManyRequests());
    }

    @Test
    void 성공한_시도는_세지_않는다() throws Exception {
        createUser("member@example.com", Role.MEMBER);
        fail("member@example.com", ip, 9);
        login("member@example.com", PASSWORD, ip, null).andExpect(status().isOk());
        fail("member@example.com", ip, 1);

        login("member@example.com", WRONG, ip, null).andExpect(status().isTooManyRequests());
    }

    @Test
    void 한_계정에_여러_IP에서_1시간에_50번_넘게_실패하면_막지_않고_경고만_남긴다_L3(CapturedOutput output) throws Exception {
        long id = createUser("member@example.com", Role.MEMBER);
        // IP와 이메일 조합 한도(10번)에 걸리지 않게 IP를 바꿔 가며 51번 실패한다.
        for (int i = 0; i < 51; i++) {
            String clientIp = "192.0.2." + (i / 9 + 1);
            login("member@example.com", WRONG, clientIp, null).andExpect(status().isUnauthorized());
        }

        assertThat(output)
                .contains("한 계정에 로그인 실패가 많음: userId=" + id + ", failures=51, distinctIps=6");
        // 입력한 이메일은 로그에 남기지 않는다 (5-1-9).
        assertThat(output).doesNotContain("member@example.com");
        login("member@example.com", PASSWORD, "192.0.2.100", null).andExpect(status().isOk());
    }
}
