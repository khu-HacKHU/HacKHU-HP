package com.hackhu.hp.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.global.auth.Role;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** access 토큰 갱신과 refresh 토큰 회전 (4-2-5, 6-RELEASE-TEST-CASES.md 6-2). */
class TokenRefreshIntegrationTest extends AuthIntegrationTestSupport {

    private long userId;

    @BeforeEach
    void member() {
        userId = createUser("member@example.com", Role.MEMBER);
    }

    private MvcResult refreshOk(String cookie) throws Exception {
        return refresh(cookie).andExpect(status().isOk()).andReturn();
    }

    private void expectRejected(String cookie) throws Exception {
        MvcResult result =
                refresh(cookie)
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                        .andReturn();
        // 쿠키를 지운다.
        assertThat(setCookieHeader(result)).startsWith("refresh_token=;").contains("Max-Age=0");
    }

    @Test
    void 현재_토큰으로_갱신하면_새_access_토큰과_새_쿠키를_준다_R1() throws Exception {
        LoggedIn loggedIn = loginOk("member@example.com");

        MvcResult result =
                refresh(loggedIn.refreshCookie())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.tokenType").value("Bearer"))
                        .andExpect(jsonPath("$.expiresIn").value(900))
                        // 프론트엔드가 원래 요청을 다시 보내도 되는지 이 값으로 확인한다 (4-2-1).
                        .andExpect(jsonPath("$.user.id").value(userId))
                        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                        .andReturn();

        String next = refreshCookieOf(result);
        assertThat(next).isNotNull().isNotEqualTo(loggedIn.refreshCookie());
        memberApi(accessTokenOf(result)).andExpect(status().isOk());
        refreshOk(next);
    }

    @Test
    void 같은_토큰으로_동시에_갱신하면_둘_다_200이고_한_응답만_새_쿠키를_준다_R2() throws Exception {
        LoggedIn loggedIn = loginOk("member@example.com");
        int requests = 2;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        List<MvcResult> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                futures.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return refresh(loggedIn.refreshCookie()).andReturn();
                                }));
            }
            start.countDown();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
        }

        assertThat(results).allMatch(r -> r.getResponse().getStatus() == 200);
        List<String> cookies =
                results.stream()
                        .map(AuthIntegrationTestSupport::refreshCookieOf)
                        .filter(Objects::nonNull)
                        .toList();
        assertThat(cookies).hasSize(1);
        // 브라우저에 남은 쿠키로 계속 갱신할 수 있다.
        refreshOk(cookies.get(0));
    }

    @Test
    void 응답을_받지_못하고_10초_안에_다시_갱신하면_access_토큰만_준다_R3() throws Exception {
        LoggedIn loggedIn = loginOk("member@example.com");
        refreshOk(loggedIn.refreshCookie());
        clock.advance(Duration.ofSeconds(9));

        MvcResult retry = refreshOk(loggedIn.refreshCookie());

        assertThat(refreshCookieOf(retry)).isNull();
        assertThat(retry.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        memberApi(accessTokenOf(retry)).andExpect(status().isOk());
    }

    @Test
    void 응답을_받지_못하고_10초가_지나_다시_갱신하면_새_쿠키를_준다_R4() throws Exception {
        LoggedIn loggedIn = loginOk("member@example.com");
        refreshOk(loggedIn.refreshCookie());
        clock.advance(Duration.ofSeconds(11));

        String recovered = refreshCookieOf(refreshOk(loggedIn.refreshCookie()));

        assertThat(recovered).isNotNull();
        refreshOk(recovered);
    }

    @Test
    void 응답_유실_복구로_폐기된_토큰을_쓰면_재사용이다_R6() throws Exception {
        LoggedIn loggedIn = loginOk("member@example.com");
        // 클라이언트가 받지 못한 토큰. 복구할 때 폐기된다.
        String lost = refreshCookieOf(refreshOk(loggedIn.refreshCookie()));
        clock.advance(Duration.ofSeconds(11));
        refreshOk(loggedIn.refreshCookie());

        expectRejected(lost);

        assertThat(
                        count(
                                "SELECT count(*) FROM login_sessions WHERE revoke_reason = 'REUSE_DETECTED'"))
                .isEqualTo(1);
    }

    @Test
    void 두_번_회전한_뒤_10초가_지난_처음_토큰은_재사용이라_세션을_폐기한다_R5() throws Exception {
        LoggedIn loggedIn = loginOk("member@example.com");
        String second = refreshCookieOf(refreshOk(loggedIn.refreshCookie()));
        MvcResult third = refreshOk(second);
        clock.advance(Duration.ofSeconds(11));

        expectRejected(loggedIn.refreshCookie());

        // 그 세션의 access 토큰과 현재 쿠키도 막힌다.
        memberApi(accessTokenOf(third)).andExpect(status().isUnauthorized());
        expectRejected(refreshCookieOf(third));
    }

    @Test
    void 몇_단계_앞이든_회전한_지_10초_안이면_동시_요청으로_받아_준다_R8() throws Exception {
        LoggedIn loggedIn = loginOk("member@example.com");
        // 탭 A가 T0로 T1을 받고 곧바로 T1로 T2를 받음
        String t1 = refreshCookieOf(refreshOk(loggedIn.refreshCookie()));
        String t2 = refreshCookieOf(refreshOk(t1));
        clock.advance(Duration.ofSeconds(5));

        // 탭 B의 T0 요청이 늦게 처리됨
        assertThat(refreshCookieOf(refreshOk(loggedIn.refreshCookie()))).isNull();

        refreshOk(t2);
    }

    @Test
    void 하루가_지나도_응답_유실은_복구한다_R9() throws Exception {
        LoggedIn loggedIn = loginOk("member@example.com");
        refreshOk(loggedIn.refreshCookie());
        clock.advance(Duration.ofDays(1));

        assertThat(refreshCookieOf(refreshOk(loggedIn.refreshCookie()))).isNotNull();
    }

    @Test
    void 두_번_회전한_지_하루가_지난_예전_토큰은_재사용이다_R10() throws Exception {
        LoggedIn loggedIn = loginOk("member@example.com");
        String second = refreshCookieOf(refreshOk(loggedIn.refreshCookie()));
        refreshOk(second);
        clock.advance(Duration.ofDays(1));

        expectRejected(loggedIn.refreshCookie());
        assertThat(activeSessions()).isZero();
    }

    @Test
    void 로그인_30일이_지나면_계속_갱신했어도_401_R7() throws Exception {
        String cookie = loginOk("member@example.com").refreshCookie();
        for (int day = 0; day < 3; day++) {
            clock.advance(Duration.ofDays(10));
            if (day < 2) {
                cookie = refreshCookieOf(refreshOk(cookie));
            }
        }

        expectRejected(cookie);
    }

    @Test
    void 쿠키의_Max_Age는_14일과_세션의_남은_수명_중_짧은_쪽이다() throws Exception {
        String cookie = loginOk("member@example.com").refreshCookie();
        clock.advance(Duration.ofDays(10));
        MvcResult day10 = refreshOk(cookie);
        assertThat(setCookieHeader(day10)).contains("Max-Age=1209600");

        clock.advance(Duration.ofDays(10));
        MvcResult day20 = refreshOk(refreshCookieOf(day10));

        assertThat(setCookieHeader(day20)).contains("Max-Age=864000");
    }

    @Test
    void 마지막_갱신부터_14일이_지나면_401() throws Exception {
        String cookie = loginOk("member@example.com").refreshCookie();
        clock.advance(Duration.ofDays(14));

        expectRejected(cookie);
    }

    @Test
    void 쿠키가_없거나_모르는_토큰이면_401() throws Exception {
        expectRejected(null);
        expectRejected("unknown-token");
    }

    @Test
    void 탈퇴한_계정이면_401() throws Exception {
        String cookie = loginOk("member@example.com").refreshCookie();
        jdbc.update(
                "UPDATE users SET email = NULL, name = NULL, password_hash = NULL, withdrawn_at = now() WHERE id = ?",
                userId);

        expectRejected(cookie);
    }

    @Test
    void 허용하지_않은_Origin이면_회전하지_않고_403_C5() throws Exception {
        LoggedIn loggedIn = loginOk("member@example.com");

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .header(HttpHeaders.ORIGIN, "http://localhost")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                REFRESH_COOKIE, loggedIn.refreshCookie())))
                .andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE replaced_at IS NOT NULL"))
                .isZero();
        refreshOk(loggedIn.refreshCookie());
    }

    @Test
    void 비밀번호_변경_필요_상태에서도_갱신한다() throws Exception {
        jdbc.update("UPDATE users SET must_change_password = true WHERE id = ?", userId);
        String cookie = loginOk("member@example.com").refreshCookie();

        refresh(cookie)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true));
    }
}
