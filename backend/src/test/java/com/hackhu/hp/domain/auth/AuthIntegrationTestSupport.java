package com.hackhu.hp.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.hackhu.hp.TestcontainersConfiguration;
import com.hackhu.hp.global.auth.CurrentUser;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인, refresh, 로그아웃과 access 토큰 필터를 실제 DB로 확인하는 테스트의 공통 준비. Docker가 필요하다.
 *
 * <p>모든 하위 클래스가 같은 설정을 써서 Spring 컨텍스트와 PostgreSQL 컨테이너를 하나만 띄운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthIntegrationTestSupport.TestBeans.class})
abstract class AuthIntegrationTestSupport {

    static final String FRONTEND = "http://localhost:5173";
    static final String PASSWORD = "correct-horse-battery";
    static final String REFRESH_COOKIE = "refresh_token";

    private static final Pattern REFRESH_COOKIE_VALUE = Pattern.compile("^refresh_token=([^;]*)");

    // bcrypt cost 12는 한 번에 0.3초 걸린다. 같은 비밀번호의 해시는 한 번만 만든다.
    private static String passwordHash;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @MockitoSpyBean PasswordEncoder passwordEncoder;

    // 요청 횟수 제한 카운터는 컨텍스트에 남는다. 테스트끼리 섞이지 않게 테스트마다 다른 IP를 쓴다.
    final String ip = randomIp();

    static String randomIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return "10.%d.%d.%d"
                .formatted(random.nextInt(256), random.nextInt(256), random.nextInt(1, 255));
    }

    @BeforeEach
    void resetState() {
        Mockito.reset(passwordEncoder);
        clock.reset();
        // 로그인 세션과 refresh 토큰은 CASCADE로 함께 지워진다.
        jdbc.update("DELETE FROM users");
    }

    String hashOf(String password) {
        if (PASSWORD.equals(password)) {
            if (passwordHash == null) {
                passwordHash = passwordEncoder.encode(PASSWORD);
            }
            return passwordHash;
        }
        return passwordEncoder.encode(password);
    }

    long createUser(String email, Role role) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, name, password_hash, role) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                email,
                "홍길동",
                hashOf(PASSWORD),
                role.name());
    }

    // 운영진이 임시 비밀번호를 발급한 상태 (3-2-5). 발급 API는 #51에서 만든다.
    void issueTemporaryPassword(long userId, String temporaryPassword, String expiresIn) {
        jdbc.update(
                """
                UPDATE users
                   SET password_hash = ?, must_change_password = true,
                       temp_password_expires_at = now() + CAST(? AS interval), temp_password_used_at = NULL
                 WHERE id = ?
                """,
                hashOf(temporaryPassword),
                expiresIn,
                userId);
    }

    MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder request, String clientIp) {
        return request.with(
                r -> {
                    r.setRemoteAddr(clientIp);
                    return r;
                });
    }

    ResultActions login(String email, String password) throws Exception {
        return login(email, password, ip, null);
    }

    ResultActions login(String email, String password, String clientIp, String existingCookie)
            throws Exception {
        MockHttpServletRequestBuilder request =
                post("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, FRONTEND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"email": "%s", "password": "%s"}
                                """
                                        .formatted(email, password));
        if (existingCookie != null) {
            request.cookie(new Cookie(REFRESH_COOKIE, existingCookie));
        }
        return mockMvc.perform(fromIp(request, clientIp));
    }

    /** 로그인에 성공했다고 보고 결과를 돌려준다. */
    LoggedIn loginOk(String email) throws Exception {
        MvcResult result = login(email, PASSWORD).andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("로그인 실패: " + result.getResponse().getStatus());
        }
        return new LoggedIn(accessTokenOf(result), refreshCookieOf(result));
    }

    ResultActions refresh(String cookie) throws Exception {
        MockHttpServletRequestBuilder request =
                post("/api/v1/auth/refresh").header(HttpHeaders.ORIGIN, FRONTEND);
        if (cookie != null) {
            request.cookie(new Cookie(REFRESH_COOKIE, cookie));
        }
        return mockMvc.perform(fromIp(request, ip));
    }

    ResultActions logout(String cookie) throws Exception {
        MockHttpServletRequestBuilder request =
                post("/api/v1/auth/logout").header(HttpHeaders.ORIGIN, FRONTEND);
        if (cookie != null) {
            request.cookie(new Cookie(REFRESH_COOKIE, cookie));
        }
        return mockMvc.perform(fromIp(request, ip));
    }

    /** 동아리원 수준의 API. 응답 본문은 "사용자 ID:역할"이다. */
    ResultActions memberApi(String accessToken) throws Exception {
        return mockMvc.perform(
                get("/api/v1/test/auth-members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    /** 운영진 수준의 API ({@code /api/v1/admin/**}). */
    ResultActions staffApi(String accessToken) throws Exception {
        return mockMvc.perform(
                get("/api/v1/admin/test/auth-staff")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    static String accessTokenOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    /** 응답이 새로 준 refresh 쿠키 값. 쿠키를 보내지 않았으면 null. */
    static String refreshCookieOf(MvcResult result) {
        List<String> headers = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        for (String header : headers) {
            Matcher matcher = REFRESH_COOKIE_VALUE.matcher(header);
            if (matcher.find() && !matcher.group(1).isEmpty()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    static String setCookieHeader(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    }

    int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    int activeSessions() {
        return count("SELECT count(*) FROM login_sessions WHERE revoked_at IS NULL");
    }

    record LoggedIn(String accessToken, String refreshCookie) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        // 서버의 모든 시각(토큰 만료, 세션 수명, 회전 시각, 요청 제한 창)을 이 시계로 정한다.
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }

        @Bean
        TestApi testApi() {
            return new TestApi();
        }
    }

    // @TestComponent: 다른 @SpringBootTest의 컴포넌트 스캔에 잡히지 않게 한다. TestBeans가 빈으로만 넣는다.
    @TestComponent
    @RestController
    static class TestApi {

        @GetMapping("/api/v1/test/auth-members")
        String members(@CurrentUser LoginUser user) {
            return user.id() + ":" + user.role();
        }

        @GetMapping("/api/v1/admin/test/auth-staff")
        String staff(@CurrentUser LoginUser user) {
            return user.id() + ":" + user.role();
        }
    }
}
