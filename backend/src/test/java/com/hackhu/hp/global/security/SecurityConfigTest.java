package com.hackhu.hp.global.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = SecurityConfigTest.TestController.class)
@Import({
    SecurityConfigTest.TestController.class,
    GlobalExceptionHandler.class,
    SecurityConfig.class
})
class SecurityConfigTest {

    private static final String FRONTEND = "http://localhost:5173";

    @Autowired MockMvc mockMvc;

    private ResultActions perform(MockHttpServletRequestBuilder request, String role)
            throws Exception {
        if (role != null) {
            // principal 이름은 사용자 ID다 (ApiAccessDeniedHandler)
            request.with(user("1").roles(role));
        }
        return mockMvc.perform(request);
    }

    private static void expectUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    private static void expectForbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("권한이 없습니다."))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Nested
    class 기본_규칙은_동아리원_이상 {

        @Test
        void 로그인하지_않으면_401() throws Exception {
            expectUnauthorized(perform(get("/api/v1/test/members"), null));
        }

        @Test
        void 승인_대기_회원은_403() throws Exception {
            expectForbidden(perform(get("/api/v1/test/members"), "PENDING"));
        }

        @Test
        void 동아리원은_통과() throws Exception {
            perform(get("/api/v1/test/members"), "MEMBER").andExpect(status().isOk());
        }

        @Test
        void 높은_역할은_낮은_역할의_수준을_통과() throws Exception {
            perform(get("/api/v1/test/members"), "STAFF").andExpect(status().isOk());
            perform(get("/api/v1/test/members"), "ADMIN").andExpect(status().isOk());
        }

        @Test
        void 없는_경로도_로그인하지_않으면_401() throws Exception {
            // 공개 목록에 없으므로 경로가 있는지 알려 주지 않는다
            expectUnauthorized(perform(get("/api/v1/no-such-path"), null));
        }
    }

    @Nested
    class 공개_API {

        @Test
        void 로그인하지_않아도_통과() throws Exception {
            perform(post("/api/v1/auth/login"), null).andExpect(status().isOk());
        }

        @Test
        void 공개_목록은_메서드별이라_다른_메서드는_401() throws Exception {
            expectUnauthorized(perform(get("/api/v1/auth/login"), null));
        }

        @Test
        void CSRF_토큰_없이_POST해도_통과() throws Exception {
            perform(post("/api/v1/auth/login"), null)
                    .andExpect(status().isOk())
                    // 서버 세션을 만들지 않는다
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        }
    }

    @Nested
    class 내_정보는_로그인_수준 {

        @Test
        void 로그인하지_않으면_401() throws Exception {
            expectUnauthorized(perform(get("/api/v1/users/me"), null));
        }

        @Test
        void 승인_대기_회원도_조회_비밀번호_변경_탈퇴는_통과() throws Exception {
            perform(get("/api/v1/users/me"), "PENDING").andExpect(status().isOk());
            perform(put("/api/v1/users/me/password"), "PENDING").andExpect(status().isOk());
            perform(delete("/api/v1/users/me"), "PENDING").andExpect(status().isOk());
        }

        @Test
        void 내_정보_아래라도_세_API가_아니면_승인_대기_회원은_403() throws Exception {
            expectForbidden(perform(get("/api/v1/users/me/settings"), "PENDING"));
        }
    }

    @Nested
    class 관리_API {

        @Test
        void 동아리원은_403() throws Exception {
            expectForbidden(perform(get("/api/v1/admin/test"), "MEMBER"));
        }

        @Test
        void 운영진은_통과() throws Exception {
            perform(get("/api/v1/admin/test"), "STAFF").andExpect(status().isOk());
        }

        @Test
        void 역할_변경은_운영진이면_403() throws Exception {
            expectForbidden(perform(put("/api/v1/admin/users/2/role"), "STAFF"));
        }

        @Test
        void 역할_변경은_관리자만_통과() throws Exception {
            perform(put("/api/v1/admin/users/2/role"), "ADMIN").andExpect(status().isOk());
        }
    }

    @Nested
    class 메서드_보안_거부도_공통_형식 {

        @Test
        void 로그인하지_않았으면_401() throws Exception {
            // URL 규칙은 통과하고 @PreAuthorize에서 거부되는 경우
            expectUnauthorized(perform(post("/api/v1/auth/signup"), null));
        }

        @Test
        void 역할이_부족하면_500이_아니라_403() throws Exception {
            expectForbidden(perform(get("/api/v1/test/staff-method"), "MEMBER"));
        }

        @Test
        void 역할이_맞으면_통과() throws Exception {
            perform(get("/api/v1/test/staff-method"), "STAFF").andExpect(status().isOk());
        }
    }

    @Nested
    class CORS {

        @Test
        void 프론트엔드_주소의_사전_요청은_허용() throws Exception {
            mockMvc.perform(
                            options("/api/v1/test/members")
                                    .header(HttpHeaders.ORIGIN, FRONTEND)
                                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                                    .header(
                                            HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                            "Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
                    .andExpect(
                            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }

        @Test
        void 다른_주소의_사전_요청은_거부() throws Exception {
            mockMvc.perform(
                            options("/api/v1/test/members")
                                    .header(HttpHeaders.ORIGIN, "https://evil.example")
                                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        void 허용하지_않은_메서드의_사전_요청은_거부() throws Exception {
            mockMvc.perform(
                            options("/api/v1/test/members")
                                    .header(HttpHeaders.ORIGIN, FRONTEND)
                                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                    .andExpect(status().isForbidden());
        }
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/test/members")
        String members() {
            return "ok";
        }

        @GetMapping("/api/v1/auth/login")
        String loginGet() {
            return "ok";
        }

        @PostMapping("/api/v1/auth/login")
        String login() {
            return "ok";
        }

        // URL 규칙상 공개지만 메서드 보안이 거부하는 경우를 만들려고 붙였다
        @PreAuthorize("hasRole('MEMBER')")
        @PostMapping("/api/v1/auth/signup")
        String signup() {
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

        @GetMapping("/api/v1/users/me/settings")
        String settings() {
            return "ok";
        }

        @GetMapping("/api/v1/admin/test")
        String admin() {
            return "ok";
        }

        @PutMapping("/api/v1/admin/users/{userId}/role")
        String changeRole(@PathVariable long userId) {
            return "ok";
        }

        @PreAuthorize("hasRole('STAFF')")
        @GetMapping("/api/v1/test/staff-method")
        String staffMethod() {
            return "ok";
        }
    }
}
