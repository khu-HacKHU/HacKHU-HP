package com.hackhu.hp.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.domain.user.dto.SignupRequest;
import com.hackhu.hp.domain.user.dto.SignupResponse;
import com.hackhu.hp.domain.user.exception.UserErrorCode;
import com.hackhu.hp.domain.user.service.SignupService;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.config.ClockConfig;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.error.GlobalExceptionHandler;
import com.hackhu.hp.global.security.RequestLimitConfig;
import com.hackhu.hp.global.security.SecurityConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(controllers = SignupController.class)
@Import({
    GlobalExceptionHandler.class,
    SecurityConfig.class,
    RequestLimitConfig.class,
    ClockConfig.class
})
class SignupControllerTest {

    private static final String URL = "/api/v1/auth/signup";
    private static final String BODY =
            """
            {"email": "newbie@example.com", "password": "correct-horse-battery", "name": "홍길동"}
            """;

    @Autowired MockMvc mockMvc;

    @MockitoBean SignupService signupService;

    private ResultActions signup(String body) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 로그인하지_않아도_가입하고_201과_Location을_준다() throws Exception {
        given(signupService.signup(any()))
                .willReturn(
                        new SignupResponse(
                                17L,
                                "newbie@example.com",
                                "홍길동",
                                Role.PENDING,
                                Instant.parse("2026-11-05T09:30:00Z")));

        signup(BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/me"))
                .andExpect(jsonPath("$.id").value(17))
                .andExpect(jsonPath("$.email").value("newbie@example.com"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.role").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-05T09:30:00Z"))
                // 가입만 하고 로그인시키지 않는다 (4-2-3).
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void 필수_항목이_비면_400과_칸별_사유를_주고_가입하지_않는다() throws Exception {
        signup("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors.length()").value(3))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')].reason").value("이메일을 입력해주세요."))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'password')].reason")
                                .value("비밀번호를 입력해주세요."))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].reason").value("이름을 입력해주세요."));

        verify(signupService, never()).signup(any());
    }

    @Test
    void 이미_가입된_이메일이면_409_AUTH_DUPLICATE_EMAIL() throws Exception {
        given(signupService.signup(any()))
                .willThrow(new BusinessException(UserErrorCode.AUTH_DUPLICATE_EMAIL));

        signup(BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("AUTH_DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));
    }

    @Test
    void 본문이_JSON이_아니면_415() throws Exception {
        // 다른 사이트의 enctype="text/plain" 폼은 JSON처럼 보이는 본문을 보낼 수 있다 (4-2-1).
        mockMvc.perform(post(URL).contentType(MediaType.TEXT_PLAIN).content(BODY))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        verify(signupService, never()).signup(any());
    }

    @Test
    void 본문에_role을_넣어도_요청에_담기지_않는다() throws Exception {
        given(signupService.signup(any()))
                .willReturn(
                        new SignupResponse(
                                17L, "newbie@example.com", "홍길동", Role.PENDING, Instant.now()));

        signup(
                        """
                        {"email": "newbie@example.com", "password": "correct-horse-battery",
                         "name": "홍길동", "role": "ADMIN", "id": 1, "approvedAt": "2026-11-05T09:30:00Z"}
                        """)
                .andExpect(status().isCreated());

        ArgumentCaptor<SignupRequest> captor = ArgumentCaptor.forClass(SignupRequest.class);
        verify(signupService).signup(captor.capture());
        assertThat(SignupRequest.class.getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly("email", "password", "name");
        assertThat(captor.getValue().email()).isEqualTo("newbie@example.com");
    }

    @Test
    void 깨진_JSON이면_400() throws Exception {
        signup("{\"email\": ").andExpect(status().isBadRequest());
    }

    // 6-RELEASE-TEST-CASES.md SU18. IP 하나로 1시간에 50번까지 받는다 (5-1-7).
    @Test
    void 같은_IP의_51번째_가입_요청은_429와_Retry_After() throws Exception {
        given(signupService.signup(any()))
                .willReturn(
                        new SignupResponse(
                                17L, "newbie@example.com", "홍길동", Role.PENDING, Instant.now()));
        String ip = "198.51.100.51";

        for (int i = 0; i < 50; i++) {
            signupFrom(ip).andExpect(status().isCreated());
        }

        signupFrom(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(
                        result ->
                                assertThat(
                                                Long.parseLong(
                                                        result.getResponse()
                                                                .getHeader("Retry-After")))
                                        .isBetween(3500L, 3600L));
        // 다른 IP는 막히지 않는다.
        signupFrom("198.51.100.52").andExpect(status().isCreated());
        verify(signupService, times(51)).signup(any());
    }

    private ResultActions signupFrom(String ip) throws Exception {
        return mockMvc.perform(
                post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(
                                request -> {
                                    request.setRemoteAddr(ip);
                                    return request;
                                }));
    }
}
