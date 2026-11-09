package com.hackhu.hp.global.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.global.auth.AccessTokenAuthenticator;
import com.hackhu.hp.global.auth.AccessTokenAuthenticator.AuthenticatedSession;
import com.hackhu.hp.global.auth.CurrentUser;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.error.GlobalExceptionHandler;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * access 토큰 검증 필터의 규칙을 본다 (5-1-3, 5-1-4). DB와 토큰 검증은 목으로 바꾸고, 실제 토큰은 {@code
 * AuthTokenIntegrationTest}가 본다.
 */
@WebMvcTest(controllers = AccessTokenAuthenticationFilterTest.TestController.class)
@Import({
    AccessTokenAuthenticationFilterTest.TestController.class,
    GlobalExceptionHandler.class,
    SecurityConfig.class
})
class AccessTokenAuthenticationFilterTest {

    private static final String FRONTEND = "http://localhost:5173";

    @Autowired MockMvc mockMvc;

    @MockitoBean AccessTokenAuthenticator authenticator;

    @BeforeEach
    void tokens() {
        given(authenticator.authenticate("member-token")).willReturn(session(Role.MEMBER, false));
        given(authenticator.authenticate("pending-token")).willReturn(session(Role.PENDING, false));
        given(authenticator.authenticate("must-change-token"))
                .willReturn(session(Role.MEMBER, true));
    }

    private static Optional<AuthenticatedSession> session(Role role, boolean mustChangePassword) {
        return Optional.of(new AuthenticatedSession(new LoginUser(7L, role), mustChangePassword));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String authorization)
            throws Exception {
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private static void expectUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
    }

    @Nested
    class 토큰_꺼내기 {

        @Test
        void 헤더가_없으면_방문자라_동아리원_API는_401() throws Exception {
            expectUnauthorized(perform(get("/api/v1/test/members"), null));
        }

        @Test
        void 받아_준_토큰이면_DB에서_읽은_사용자로_통과() throws Exception {
            perform(get("/api/v1/test/members"), "Bearer member-token")
                    .andExpect(status().isOk())
                    .andExpect(content().string("7:MEMBER"));
        }

        @Test
        void 인증_방식_이름은_대소문자를_가리지_않는다() throws Exception {
            perform(get("/api/v1/test/members"), "bearer member-token").andExpect(status().isOk());
        }

        @Test
        void 받아_주지_못한_토큰은_401_T12() throws Exception {
            expectUnauthorized(perform(get("/api/v1/test/members"), "Bearer expired-or-forged"));
        }

        @Test
        void 공개_API여도_받아_주지_못한_토큰이면_401() throws Exception {
            // 프론트엔드는 이 401을 받고 refresh를 한 번 시도한다 (A13).
            expectUnauthorized(perform(get("/api/v1/albums"), "Bearer expired-or-forged"));
        }

        @Test
        void Bearer가_아니거나_토큰이_비면_401이고_검증하지_않는다() throws Exception {
            expectUnauthorized(perform(get("/api/v1/test/members"), "Basic dXNlcjpwdw=="));
            expectUnauthorized(perform(get("/api/v1/test/members"), "Bearer "));
            expectUnauthorized(perform(get("/api/v1/test/members"), "Bearer"));
            verify(authenticator, never()).authenticate(anyString());
        }

        @Test
        void 인증_API는_Authorization_헤더를_보지_않는다() throws Exception {
            // 만료된 access 토큰이 실린 로그인 요청이 401로 막히지 않는다.
            perform(
                            post("/api/v1/auth/login").header(HttpHeaders.ORIGIN, FRONTEND),
                            "Bearer expired-or-forged")
                    .andExpect(status().isOk());
            verify(authenticator, never()).authenticate(anyString());
        }
    }

    @Nested
    class 승인_대기_회원 {

        @Test
        void 동아리원_API는_403() throws Exception {
            perform(get("/api/v1/test/members"), "Bearer pending-token")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }

        @Test
        void 내_정보는_통과() throws Exception {
            perform(get("/api/v1/users/me"), "Bearer pending-token").andExpect(status().isOk());
        }
    }

    @Nested
    class 비밀번호_변경_필요_상태 {

        private void expectPasswordChangeRequired(ResultActions result) throws Exception {
            result.andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_CHANGE_REQUIRED"));
        }

        @Test
        void 다른_API는_403_AUTH_PASSWORD_CHANGE_REQUIRED() throws Exception {
            expectPasswordChangeRequired(
                    perform(get("/api/v1/test/members"), "Bearer must-change-token"));
            expectPasswordChangeRequired(
                    perform(delete("/api/v1/users/me"), "Bearer must-change-token"));
        }

        @Test
        void 내_정보_조회와_비밀번호_변경은_통과() throws Exception {
            perform(get("/api/v1/users/me"), "Bearer must-change-token").andExpect(status().isOk());
            perform(put("/api/v1/users/me/password"), "Bearer must-change-token")
                    .andExpect(status().isOk());
        }

        @Test
        void refresh와_로그아웃은_통과() throws Exception {
            perform(
                            post("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN, FRONTEND),
                            "Bearer must-change-token")
                    .andExpect(status().isOk());
            perform(
                            post("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, FRONTEND),
                            "Bearer must-change-token")
                    .andExpect(status().isOk());
        }
    }

    // @TestComponent: 다른 @SpringBootTest의 컴포넌트 스캔에 잡히지 않게 한다. 이 테스트는 @Import로만 넣는다.
    @TestComponent
    @RestController
    static class TestController {

        @GetMapping("/api/v1/test/members")
        String members(@CurrentUser LoginUser user) {
            return user.id() + ":" + user.role();
        }

        @GetMapping("/api/v1/albums")
        String albums() {
            return "ok";
        }

        @GetMapping("/api/v1/users/me")
        String me() {
            return "ok";
        }

        @PutMapping("/api/v1/users/me/password")
        String changePassword() {
            return "ok";
        }

        @DeleteMapping("/api/v1/users/me")
        String withdraw() {
            return "ok";
        }

        @PostMapping("/api/v1/auth/login")
        String login() {
            return "ok";
        }

        @PostMapping("/api/v1/auth/refresh")
        String refresh() {
            return "ok";
        }

        @PostMapping("/api/v1/auth/logout")
        String logout() {
            return "ok";
        }
    }
}
