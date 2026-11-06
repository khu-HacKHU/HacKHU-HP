package com.hackhu.hp.global.auth;

import static com.hackhu.hp.global.auth.LoginUserRequests.loginUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.global.error.GlobalExceptionHandler;
import com.hackhu.hp.global.security.SecurityConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = CurrentUserTest.TestController.class)
@Import({CurrentUserTest.TestController.class, GlobalExceptionHandler.class, SecurityConfig.class})
class CurrentUserTest {

    @Autowired MockMvc mockMvc;

    @Nested
    class 로그인이_필요한_API {

        @Test
        void 현재_사용자의_ID와_역할을_받는다() throws Exception {
            mockMvc.perform(get("/api/v1/test/current-user").with(loginUser(7L, Role.MEMBER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("7:MEMBER"));
        }

        @Test
        @WithLoginUser(id = 9, role = Role.ADMIN)
        void WithLoginUser로_로그인_사용자를_정한다() throws Exception {
            mockMvc.perform(get("/api/v1/test/current-user"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("9:ADMIN"));
        }

        @Test
        void 비로그인이면_컨트롤러에_오기_전에_401() throws Exception {
            mockMvc.perform(get("/api/v1/test/current-user"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }

        @Test
        void 승인_대기_회원은_403() throws Exception {
            mockMvc.perform(get("/api/v1/test/current-user").with(loginUser(7L, Role.PENDING)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Nested
    class 공개_API {

        @Test
        void 비로그인이면_null이다() throws Exception {
            mockMvc.perform(post("/api/v1/auth/signup"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("visitor"));
        }

        @Test
        void 승인_대기_회원은_방문자와_같은_결과를_받는다() throws Exception {
            mockMvc.perform(post("/api/v1/auth/signup").with(loginUser(7L, Role.PENDING)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("visitor"));
        }

        @Test
        void 동아리원은_동아리원으로_다뤄진다() throws Exception {
            mockMvc.perform(post("/api/v1/auth/signup").with(loginUser(7L, Role.MEMBER)))
                    .andExpect(status().isOk())
                    .andExpect(content().string("member"));
        }
    }

    @Nested
    class 메서드_보안 {

        @Test
        void 운영진_전용은_운영진과_관리자만_통과한다() throws Exception {
            mockMvc.perform(get("/api/v1/test/staff").with(loginUser(1L, Role.STAFF)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/test/staff").with(loginUser(1L, Role.ADMIN)))
                    .andExpect(status().isOk());
        }

        @Test
        void 동아리원은_운영진_전용에서_403() throws Exception {
            mockMvc.perform(get("/api/v1/test/staff").with(loginUser(1L, Role.MEMBER)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
    }

    @Test
    void LoginUser가_아닌_테스트_사용자는_null로_들어온다() throws Exception {
        // with(user(...))로는 @CurrentUser를 테스트할 수 없다는 것을 고정한다 (LoginUserRequests 참고)
        mockMvc.perform(get("/api/v1/test/current-user-or-null").with(user("1").roles("MEMBER")))
                .andExpect(status().isOk())
                .andExpect(content().string("null"));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/test/current-user")
        String currentUser(@CurrentUser LoginUser user) {
            return user.id() + ":" + user.role();
        }

        @GetMapping("/api/v1/test/current-user-or-null")
        String currentUserOrNull(@CurrentUser LoginUser user) {
            return String.valueOf(user);
        }

        // 공개 API에서 동아리원 여부로 응답을 나누는 경우. 공개 경로를 빌려 쓴다.
        @PostMapping("/api/v1/auth/signup")
        String publicApi(@CurrentUser LoginUser user) {
            boolean member = user != null && user.hasRoleAtLeast(Role.MEMBER);
            return member ? "member" : "visitor";
        }

        @PreAuthorize("hasRole('STAFF')")
        @GetMapping("/api/v1/test/staff")
        String staff(@CurrentUser LoginUser user) {
            return "ok";
        }
    }
}
