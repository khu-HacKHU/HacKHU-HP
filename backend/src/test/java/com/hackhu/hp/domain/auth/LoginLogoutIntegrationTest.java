package com.hackhu.hp.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.global.auth.AccessTokenService;
import com.hackhu.hp.global.auth.JwtProperties;
import com.hackhu.hp.global.auth.Role;
import com.nimbusds.jwt.SignedJWT;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** 로그인, 로그아웃, 요청마다 하는 인증 (4-2-4, 4-2-6, 5-1-4, 6-RELEASE-TEST-CASES.md 6-1, 6-3, 6-4, 6-10). */
class LoginLogoutIntegrationTest extends AuthIntegrationTestSupport {

    @Autowired AccessTokenService accessTokenService;

    @Nested
    class 로그인 {

        @Test
        void 로그인하면_access_토큰과_사용자_정보를_주고_refresh_토큰은_쿠키로_준다_LG1() throws Exception {
            long id = createUser("member@example.com", Role.MEMBER);

            MvcResult result =
                    login("member@example.com", PASSWORD)
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.tokenType").value("Bearer"))
                            .andExpect(jsonPath("$.expiresIn").value(900))
                            .andExpect(jsonPath("$.user.id").value(id))
                            .andExpect(jsonPath("$.user.email").value("member@example.com"))
                            .andExpect(jsonPath("$.user.name").value("홍길동"))
                            .andExpect(jsonPath("$.user.role").value("MEMBER"))
                            .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                            .andExpect(jsonPath("$.refreshToken").doesNotExist())
                            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                            .andReturn();

            // 토큰에는 역할과 개인정보를 넣지 않는다 (5-1-5).
            assertThat(SignedJWT.parse(accessTokenOf(result)).getJWTClaimsSet().getClaims())
                    .containsOnlyKeys("iss", "sub", "sid", "iat", "exp");
            assertThat(activeSessions()).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM refresh_tokens")).isEqualTo(1);
            // 원문이 아니라 해시만 저장한다.
            assertThat(
                            count(
                                    "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
                                    refreshCookieOf(result)))
                    .isZero();
        }

        @Test
        void refresh_쿠키의_속성_C6() throws Exception {
            createUser("member@example.com", Role.MEMBER);

            String cookie = setCookieHeader(login("member@example.com", PASSWORD).andReturn());

            assertThat(cookie)
                    .startsWith("refresh_token=")
                    .contains("Path=/api/v1/auth")
                    .contains("Max-Age=1209600")
                    .contains("HttpOnly")
                    .contains("Secure")
                    .contains("SameSite=Strict")
                    .doesNotContainIgnoringCase("Domain=");
        }

        @Test
        void 이메일과_비밀번호_중_어느_쪽이_틀렸는지_알려_주지_않는다_LG2() throws Exception {
            createUser("member@example.com", Role.MEMBER);

            String wrongPassword =
                    login("member@example.com", "wrong-password-123")
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                            .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            String unknownEmail =
                    login("nobody@example.com", PASSWORD)
                            .andExpect(status().isUnauthorized())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            assertThat(unknownEmail).isEqualTo(wrongPassword);
            assertThat(count("SELECT count(*) FROM login_sessions")).isZero();
        }

        @Test
        void 가입되지_않은_이메일도_bcrypt를_계산한다() throws Exception {
            login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized());

            // 응답 시간으로 계정이 있는지 알 수 없게 더미 해시와 비교한다 (CWE-208).
            org.mockito.Mockito.verify(passwordEncoder).matches(eq(PASSWORD), anyString());
        }

        @Test
        void 탈퇴한_계정은_로그인되지_않는다() throws Exception {
            long id = createUser("member@example.com", Role.MEMBER);
            jdbc.update(
                    "UPDATE users SET email = NULL, name = NULL, password_hash = NULL, withdrawn_at = now() WHERE id = ?",
                    id);

            login("member@example.com", PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        }

        @Test
        void 이메일은_가입과_같이_공백과_대소문자를_정리한다() throws Exception {
            createUser("member@example.com", Role.MEMBER);

            login("  Member@Example.COM ", PASSWORD).andExpect(status().isOk());
        }

        @Test
        void 비밀번호가_72바이트를_넘으면_500이_아니라_401() throws Exception {
            createUser("member@example.com", Role.MEMBER);

            login("member@example.com", "가".repeat(30)).andExpect(status().isUnauthorized());
        }

        @Test
        void 이메일이나_비밀번호가_비면_400() throws Exception {
            login("", "")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.errors.length()").value(2));
        }

        @Test
        void 다른_사이트의_text_plain_폼은_415이고_세션이_생기지_않는다_C4() throws Exception {
            createUser("member@example.com", Role.MEMBER);

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .header(HttpHeaders.ORIGIN, FRONTEND)
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .content(
                                            "{\"email\": \"member@example.com\", \"password\": \"%s\", \"x\": \"=\"}"
                                                    .formatted(PASSWORD)))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
            assertThat(count("SELECT count(*) FROM login_sessions")).isZero();
        }

        @Test
        void 허용하지_않은_Origin이면_403이고_세션이_생기지_않는다_C5() throws Exception {
            createUser("member@example.com", Role.MEMBER);

            mockMvc.perform(
                            post("/api/v1/auth/login")
                                    .header(HttpHeaders.ORIGIN, "http://localhost")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            """
                                            {"email": "member@example.com", "password": "%s"}
                                            """
                                                    .formatted(PASSWORD)))
                    .andExpect(status().isForbidden());
            assertThat(count("SELECT count(*) FROM login_sessions")).isZero();
        }

        @Test
        void 같은_브라우저에서_다시_로그인하면_기존_세션을_폐기한다_T13() throws Exception {
            createUser("member@example.com", Role.MEMBER);
            LoggedIn first = loginOk("member@example.com");

            login("member@example.com", PASSWORD, ip, first.refreshCookie())
                    .andExpect(status().isOk());

            memberApi(first.accessToken()).andExpect(status().isUnauthorized());
            assertThat(activeSessions()).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM login_sessions WHERE revoke_reason = 'LOGOUT'"))
                    .isEqualTo(1);
        }

        @Test
        void 다시_로그인에_실패하면_기존_세션을_건드리지_않는다() throws Exception {
            createUser("member@example.com", Role.MEMBER);
            LoggedIn first = loginOk("member@example.com");

            login("member@example.com", "wrong-password-123", ip, first.refreshCookie())
                    .andExpect(status().isUnauthorized());

            memberApi(first.accessToken()).andExpect(status().isOk());
        }
    }

    @Nested
    class 승인_대기_회원 {

        @Test
        void 로그인되지만_동아리원_API는_403_LG3() throws Exception {
            createUser("pending@example.com", Role.PENDING);

            MvcResult result =
                    login("pending@example.com", PASSWORD)
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.user.role").value("PENDING"))
                            .andReturn();

            memberApi(accessTokenOf(result))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        void 승인되면_다시_로그인하지_않아도_다음_요청부터_동아리원_API를_쓴다() throws Exception {
            long id = createUser("pending@example.com", Role.PENDING);
            LoggedIn loggedIn = loginOk("pending@example.com");

            jdbc.update("UPDATE users SET role = 'MEMBER', approved_at = now() WHERE id = ?", id);

            memberApi(loggedIn.accessToken())
                    .andExpect(status().isOk())
                    .andExpect(content().string(id + ":MEMBER"));
        }
    }

    @Nested
    class 로그아웃 {

        @Test
        void 로그아웃하면_그_세션의_access_토큰이_만료_전이라도_401_T1() throws Exception {
            createUser("member@example.com", Role.MEMBER);
            LoggedIn loggedIn = loginOk("member@example.com");
            memberApi(loggedIn.accessToken()).andExpect(status().isOk());

            MvcResult result =
                    logout(loggedIn.refreshCookie()).andExpect(status().isNoContent()).andReturn();

            assertThat(setCookieHeader(result)).startsWith("refresh_token=;").contains("Max-Age=0");
            memberApi(loggedIn.accessToken())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
            // 다시 로그인하기 전까지 refresh로도 되살릴 수 없다.
            refresh(loggedIn.refreshCookie()).andExpect(status().isUnauthorized());
        }

        @Test
        void 다른_기기의_로그인은_유지된다_T2() throws Exception {
            createUser("member@example.com", Role.MEMBER);
            LoggedIn deviceA = loginOk("member@example.com");
            LoggedIn deviceB = loginOk("member@example.com");

            logout(deviceA.refreshCookie()).andExpect(status().isNoContent());

            memberApi(deviceA.accessToken()).andExpect(status().isUnauthorized());
            memberApi(deviceB.accessToken()).andExpect(status().isOk());
        }

        @Test
        void 회전한_예전_쿠키로도_그_세션을_로그아웃한다() throws Exception {
            createUser("member@example.com", Role.MEMBER);
            LoggedIn loggedIn = loginOk("member@example.com");
            refresh(loggedIn.refreshCookie()).andExpect(status().isOk());

            logout(loggedIn.refreshCookie()).andExpect(status().isNoContent());

            assertThat(activeSessions()).isZero();
        }

        @Test
        void 쿠키가_없거나_모르는_토큰이거나_이미_로그아웃했어도_204() throws Exception {
            createUser("member@example.com", Role.MEMBER);
            LoggedIn loggedIn = loginOk("member@example.com");
            logout(loggedIn.refreshCookie()).andExpect(status().isNoContent());

            logout(loggedIn.refreshCookie()).andExpect(status().isNoContent());
            logout(null).andExpect(status().isNoContent());
            logout("unknown-token").andExpect(status().isNoContent());
        }

        @Test
        void 허용하지_않은_Origin이면_세션을_건드리지_않고_403_C5() throws Exception {
            createUser("member@example.com", Role.MEMBER);
            LoggedIn loggedIn = loginOk("member@example.com");

            mockMvc.perform(
                            post("/api/v1/auth/logout")
                                    .cookie(
                                            new jakarta.servlet.http.Cookie(
                                                    REFRESH_COOKIE, loggedIn.refreshCookie())))
                    .andExpect(status().isForbidden());

            memberApi(loggedIn.accessToken()).andExpect(status().isOk());
        }
    }

    @Nested
    class 요청마다_하는_인증 {

        @Test
        void 역할을_내리면_같은_토큰으로도_다음_요청부터_403_T6() throws Exception {
            long id = createUser("staff@example.com", Role.STAFF);
            LoggedIn loggedIn = loginOk("staff@example.com");
            staffApi(loggedIn.accessToken()).andExpect(status().isOk());

            jdbc.update("UPDATE users SET role = 'MEMBER' WHERE id = ?", id);

            staffApi(loggedIn.accessToken()).andExpect(status().isForbidden());
            memberApi(loggedIn.accessToken()).andExpect(status().isOk());
        }

        @Test
        void 세션이_폐기되면_access_토큰이_401_T5() throws Exception {
            long id = createUser("member@example.com", Role.MEMBER);
            LoggedIn loggedIn = loginOk("member@example.com");

            // 임시 비밀번호 발급(#51)이 대상의 세션을 모두 폐기하는 것과 같다.
            jdbc.update(
                    "UPDATE login_sessions SET revoked_at = now(), revoke_reason = 'PASSWORD_RESET' WHERE user_id = ?",
                    id);

            memberApi(loggedIn.accessToken()).andExpect(status().isUnauthorized());
        }

        @Test
        void 탈퇴한_계정의_토큰은_401_T7() throws Exception {
            long id = createUser("member@example.com", Role.MEMBER);
            LoggedIn loggedIn = loginOk("member@example.com");

            jdbc.update(
                    "UPDATE users SET email = NULL, name = NULL, password_hash = NULL, withdrawn_at = now() WHERE id = ?",
                    id);

            memberApi(loggedIn.accessToken()).andExpect(status().isUnauthorized());
        }

        @Test
        void 만료된_토큰과_서명이_틀린_토큰은_같은_401_T12() throws Exception {
            createUser("member@example.com", Role.MEMBER);
            LoggedIn loggedIn = loginOk("member@example.com");
            byte[] otherKey = new byte[32];
            new SecureRandom().nextBytes(otherKey);
            String forged =
                    new AccessTokenService(
                                    new JwtProperties(
                                            Base64.getEncoder().encodeToString(otherKey), false),
                                    clock)
                            .issue(1L, UUID.randomUUID())
                            .value();

            String forgedResponse =
                    memberApi(forged)
                            .andExpect(status().isUnauthorized())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            clock.advance(Duration.ofMinutes(15));
            String expiredResponse =
                    memberApi(loggedIn.accessToken())
                            .andExpect(status().isUnauthorized())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();

            assertThat(expiredResponse).isEqualTo(forgedResponse).contains("\"UNAUTHORIZED\"");
        }

        @Test
        void 다른_사용자의_세션_ID를_담은_토큰은_401() throws Exception {
            createUser("member@example.com", Role.MEMBER);
            long other = createUser("other@example.com", Role.ADMIN);
            loginOk("member@example.com");
            UUID sessionOfMember = jdbc.queryForObject("SELECT id FROM login_sessions", UUID.class);

            // 서명 키가 있어도 세션의 사용자와 sub가 다르면 거부한다.
            String token = accessTokenService.issue(other, sessionOfMember).value();

            memberApi(token).andExpect(status().isUnauthorized());
        }

        @Test
        void 비밀번호_변경_필요_상태면_다른_API는_403() throws Exception {
            long id = createUser("admin@example.com", Role.ADMIN);
            // 처음 관리자(#23)처럼 첫 로그인에 비밀번호를 바꿔야 하는 계정
            jdbc.update("UPDATE users SET must_change_password = true WHERE id = ?", id);

            MvcResult result =
                    login("admin@example.com", PASSWORD)
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                            .andReturn();

            memberApi(accessTokenOf(result))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_CHANGE_REQUIRED"));
        }
    }

    @Nested
    class 임시_비밀번호 {

        private static final String TEMP = "Tmp7kQ2xRw9pZm4N";

        @Test
        void 한_번만_로그인되고_비밀번호_변경_필요_상태다_TP2_CC12() throws Exception {
            long id = createUser("member@example.com", Role.MEMBER);
            issueTemporaryPassword(id, TEMP, "24 hours");

            login("member@example.com", TEMP)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.mustChangePassword").value(true));

            // 일반 로그인 경로로 빠지지 않는다.
            login("member@example.com", TEMP)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
            assertThat(
                            count(
                                    "SELECT count(*) FROM users WHERE id = ? AND temp_password_used_at IS NOT NULL",
                                    id))
                    .isEqualTo(1);
        }

        @Test
        void 만료된_임시_비밀번호는_401_TP4() throws Exception {
            long id = createUser("member@example.com", Role.MEMBER);
            issueTemporaryPassword(id, TEMP, "-1 minute");

            login("member@example.com", TEMP).andExpect(status().isUnauthorized());
            assertThat(count("SELECT count(*) FROM login_sessions")).isZero();
        }

        @Test
        void 원래_비밀번호는_더_이상_쓸_수_없다() throws Exception {
            long id = createUser("member@example.com", Role.MEMBER);
            issueTemporaryPassword(id, TEMP, "24 hours");

            login("member@example.com", PASSWORD).andExpect(status().isUnauthorized());
        }

        @Test
        void 같은_임시_비밀번호로_동시에_로그인하면_하나만_성공한다_TP1() throws Exception {
            long id = createUser("member@example.com", Role.MEMBER);
            issueTemporaryPassword(id, TEMP, "24 hours");
            int requests = 4;
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();

            try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
                for (int i = 0; i < requests; i++) {
                    results.add(
                            executor.submit(
                                    () -> {
                                        start.await();
                                        return login("member@example.com", TEMP)
                                                .andReturn()
                                                .getResponse()
                                                .getStatus();
                                    }));
                }
                start.countDown();
                List<Integer> statuses = new ArrayList<>();
                for (Future<Integer> result : results) {
                    statuses.add(result.get());
                }

                assertThat(statuses)
                        .containsOnlyOnce(200)
                        .filteredOn(s -> s != 200)
                        .containsOnly(401);
            }
            assertThat(count("SELECT count(*) FROM login_sessions")).isEqualTo(1);
        }

        @Test
        void 검증_직후_새_임시_비밀번호가_발급되면_옛_것은_401이고_새_것은_쓸_수_있다_TP3() throws Exception {
            long id = createUser("member@example.com", Role.MEMBER);
            issueTemporaryPassword(id, TEMP, "24 hours");
            String newTemp = "New9kQ2xRw9pZm4M";
            String newTempHash = hashOf(newTemp);
            doAnswer(
                            invocation -> {
                                boolean matched = (boolean) invocation.callRealMethod();
                                jdbc.update(
                                        """
                                        UPDATE users SET password_hash = ?, temp_password_used_at = NULL,
                                               temp_password_expires_at = now() + interval '24 hours'
                                         WHERE id = ?
                                        """,
                                        newTempHash,
                                        id);
                                return matched;
                            })
                    .when(passwordEncoder)
                    .matches(eq(TEMP), anyString());

            login("member@example.com", TEMP).andExpect(status().isUnauthorized());

            assertThat(count("SELECT count(*) FROM login_sessions")).isZero();
            assertThat(
                            count(
                                    "SELECT count(*) FROM users WHERE id = ? AND temp_password_used_at IS NULL",
                                    id))
                    .isEqualTo(1);
            login("member@example.com", newTemp).andExpect(status().isOk());
        }
    }

    @Nested
    class 검증과_세션_생성_사이의_변경 {

        @Test
        void 검증_직후_비밀번호가_초기화되면_옛_비밀번호로_세션이_생기지_않는다_CC1() throws Exception {
            long id = createUser("member@example.com", Role.MEMBER);
            String resetHash = hashOf("Tmp7kQ2xRw9pZm4N");
            doAnswer(
                            invocation -> {
                                boolean matched = (boolean) invocation.callRealMethod();
                                jdbc.update(
                                        """
                                        UPDATE users SET password_hash = ?, must_change_password = true,
                                               temp_password_expires_at = now() + interval '24 hours'
                                         WHERE id = ?
                                        """,
                                        resetHash,
                                        id);
                                return matched;
                            })
                    .when(passwordEncoder)
                    .matches(eq(PASSWORD), anyString());

            login("member@example.com", PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));

            assertThat(count("SELECT count(*) FROM login_sessions")).isZero();
        }

        @Test
        void 검증_직후_탈퇴하면_세션이_생기지_않는다_CC3() throws Exception {
            long id = createUser("member@example.com", Role.MEMBER);
            doAnswer(
                            invocation -> {
                                boolean matched = (boolean) invocation.callRealMethod();
                                jdbc.update(
                                        "UPDATE users SET email = NULL, name = NULL, password_hash = NULL, withdrawn_at = now() WHERE id = ?",
                                        id);
                                return matched;
                            })
                    .when(passwordEncoder)
                    .matches(eq(PASSWORD), anyString());

            login("member@example.com", PASSWORD).andExpect(status().isUnauthorized());

            assertThat(count("SELECT count(*) FROM login_sessions")).isZero();
        }
    }
}
