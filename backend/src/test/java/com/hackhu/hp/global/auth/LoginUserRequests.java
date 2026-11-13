package com.hackhu.hp.global.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc 요청에 로그인 사용자를 싣는 테스트 도우미.
 *
 * <pre>{@code
 * mockMvc.perform(post("/api/v1/notices").with(loginUser(1L, Role.STAFF)))
 * }</pre>
 *
 * <p>{@code with(user("1").roles("MEMBER"))}로 만든 사용자는 {@link LoginUser}가 아니라서 {@link CurrentUser}
 * 파라미터가 null이 된다. {@code @CurrentUser}를 쓰는 API는 이 도우미나 {@link WithLoginUser}로 테스트한다.
 */
public final class LoginUserRequests {

    private LoginUserRequests() {}

    public static RequestPostProcessor loginUser(long id, Role role) {
        return authentication(new LoginUserAuthentication(new LoginUser(id, role)));
    }
}
