package com.hackhu.hp.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.global.auth.AccessTokenService;
import com.hackhu.hp.global.auth.JwtProperties;
import com.hackhu.hp.global.auth.Role;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 대표 API의 인증 거부 (#27, #144, 6-RELEASE-TEST-CASES.md 6-12).
 *
 * <p>테스트 전용 컨트롤러가 아니라 실제 API를 실제 토큰, 필터, DB로 부른다. 경로 규칙이나 필터가 깨지면 여기서 실패한다.
 *
 * <p>토큰 필터는 모든 경로에 공통이므로 동아리원 API는 앨범 API 두 개로, 운영진 권한은 공지 작성으로 대표한다. 경로별 권한 규칙은 각 API의 테스트가 보므로 새
 * API를 만들어도 이 파일에 더하지 않는다.
 *
 * <p>앨범 API의 요청 본문은 일부러 입력 검증에 걸리게 보낸다. 인증을 통과한 동아리원은 400을 받으므로 경로가 실제로 있고 거부가 입력 검증보다 먼저라는 것을 함께
 * 확인한다. 검증을 통과하는 본문을 보내지 않으므로 S3 주소 발급이나 앨범 저장은 일어나지 않는다.
 */
class ProtectedApiDenialIntegrationTest extends AuthIntegrationTestSupport {

    /** 대표로 부르는 동아리원 API (SecurityConfig 기본 규칙, gallery 4-2 3, 4절). */
    enum ProtectedApi {
        UPLOAD_URLS("/api/v1/albums/upload-urls", "{\"files\": []}"),
        CREATE_ALBUM("/api/v1/albums", "{}");

        final String path;
        final String invalidBody;

        ProtectedApi(String path, String invalidBody) {
            this.path = path;
            this.invalidBody = invalidBody;
        }
    }

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    // post는 users를 참조하므로 먼저 지워야 다음 테스트의 준비(DELETE FROM users)가 실패하지 않는다.
    @AfterEach
    void removePosts() {
        jdbc.update("DELETE FROM post");
    }

    private ResultActions call(ProtectedApi api, Consumer<MockHttpServletRequestBuilder> customizer)
            throws Exception {
        MockHttpServletRequestBuilder request =
                post(api.path).contentType(MediaType.APPLICATION_JSON).content(api.invalidBody);
        customizer.accept(request);
        return mockMvc.perform(request);
    }

    private ResultActions callWithToken(ProtectedApi api, String token) throws Exception {
        return call(api, r -> r.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static String expectUnauthorized(ResultActions result) throws Exception {
        return result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private UUID sessionIdOf(long userId) {
        return jdbc.queryForObject(
                "SELECT id FROM login_sessions WHERE user_id = ?", UUID.class, userId);
    }

    // JWT의 세 부분(헤더.본문.서명)을 나눈다.
    private static String[] partsOf(String token) {
        String[] parts = token.split("\\.", -1);
        assertThat(parts).hasSize(3);
        return parts;
    }

    private static String encodeJson(String json) {
        return BASE64_URL.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(ProtectedApi.class)
    void 동아리원은_인증을_통과해_입력_검증까지_간다(ProtectedApi api) throws Exception {
        createUser("member@example.com", Role.MEMBER);
        LoggedIn member = loginOk("member@example.com");

        callWithToken(api, member.accessToken())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @ParameterizedTest
    @EnumSource(ProtectedApi.class)
    void 로그인하지_않고_부르면_401_D1(ProtectedApi api) throws Exception {
        expectUnauthorized(call(api, r -> {}));
    }

    @ParameterizedTest
    @EnumSource(ProtectedApi.class)
    void 토큰을_헤더가_아닌_쿼리나_쿠키로_보내면_401_D2(ProtectedApi api) throws Exception {
        createUser("member@example.com", Role.MEMBER);
        String token = loginOk("member@example.com").accessToken();

        expectUnauthorized(call(api, r -> r.queryParam("access_token", token)));
        expectUnauthorized(call(api, r -> r.cookie(new Cookie("access_token", token))));
        // Bearer가 아닌 인증 방식
        expectUnauthorized(call(api, r -> r.header(HttpHeaders.AUTHORIZATION, token)));
    }

    @ParameterizedTest
    @EnumSource(ProtectedApi.class)
    void 위조한_토큰은_모두_401이고_로그인하지_않은_것과_같은_응답이다_D3(ProtectedApi api) throws Exception {
        long memberId = createUser("member@example.com", Role.MEMBER);
        long adminId = createUser("admin@example.com", Role.ADMIN);
        String real = loginOk("member@example.com").accessToken();
        UUID sessionId = sessionIdOf(memberId);
        String[] parts = partsOf(real);

        Map<String, String> forged = new LinkedHashMap<>();
        forged.put("토큰 형식이 아님", "not-a-jwt");
        // 같은 사용자와 살아 있는 세션을 담았지만 서버 키가 아닌 키로 서명함
        byte[] otherKey = new byte[32];
        new SecureRandom().nextBytes(otherKey);
        forged.put(
                "다른 키로 서명",
                new AccessTokenService(
                                new JwtProperties(
                                        Base64.getEncoder().encodeToString(otherKey), false),
                                clock)
                        .issue(memberId, sessionId)
                        .value());
        // 본문의 sub를 관리자 ID로 바꾸고 원래 서명을 그대로 둠
        String payload = new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        String tamperedPayload =
                payload.replaceFirst("\"sub\":\"\\d+\"", "\"sub\":\"" + adminId + "\"");
        assertThat(tamperedPayload).isNotEqualTo(payload);
        forged.put(
                "본문을 바꾸고 서명은 그대로", parts[0] + "." + encodeJson(tamperedPayload) + "." + parts[2]);
        // 서명 검증을 건너뛰게 하려는 alg: none (CWE-347)
        forged.put(
                "alg none",
                encodeJson("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + parts[1] + ".");
        // 서명 부분을 지움
        forged.put("서명 없음", parts[0] + "." + parts[1] + ".");

        String anonymous = expectUnauthorized(call(api, r -> {}));
        for (Map.Entry<String, String> entry : forged.entrySet()) {
            String response = expectUnauthorized(callWithToken(api, entry.getValue()));
            assertThat(response).as(entry.getKey()).isEqualTo(anonymous);
        }
        // 위조한 토큰을 받는 동안에도 진짜 토큰은 그대로 쓸 수 있다
        callWithToken(api, real).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(ProtectedApi.class)
    void 만료된_토큰은_401이고_로그인하지_않은_것과_같은_응답이다_D4(ProtectedApi api) throws Exception {
        createUser("member@example.com", Role.MEMBER);
        String token = loginOk("member@example.com").accessToken();
        String anonymous = expectUnauthorized(call(api, r -> {}));

        // 만료 1초 전에는 통과한다. 세션은 살아 있으므로 401의 원인은 토큰 만료뿐이다.
        clock.advance(AccessTokenService.LIFETIME.minusSeconds(1));
        callWithToken(api, token).andExpect(status().isBadRequest());

        clock.advance(Duration.ofSeconds(1));
        assertThat(expectUnauthorized(callWithToken(api, token))).isEqualTo(anonymous);
    }

    @ParameterizedTest
    @EnumSource(ProtectedApi.class)
    void 가입_API로_가입한_승인_대기_회원은_403_D5(ProtectedApi api) throws Exception {
        mockMvc.perform(
                        fromIp(
                                post("/api/v1/auth/signup")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email": "newbie@example.com", "password": "%s", "name": "홍길동"}
                                                """
                                                        .formatted(PASSWORD)),
                                ip))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PENDING"));
        String token = loginOk("newbie@example.com").accessToken();

        // 입력 검증(400)보다 먼저 역할에서 막힌다
        callWithToken(api, token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @EnumSource(ProtectedApi.class)
    void 로그아웃한_세션의_토큰은_만료_전이라도_401이고_로그인하지_않은_것과_같은_응답이다_D6(ProtectedApi api) throws Exception {
        createUser("member@example.com", Role.MEMBER);
        LoggedIn member = loginOk("member@example.com");
        String anonymous = expectUnauthorized(call(api, r -> {}));
        callWithToken(api, member.accessToken()).andExpect(status().isBadRequest());

        logout(member.refreshCookie()).andExpect(status().isNoContent());

        assertThat(expectUnauthorized(callWithToken(api, member.accessToken())))
                .isEqualTo(anonymous);
    }

    @Test
    void 운영진에서_내려가면_같은_토큰으로_공지를_쓸_수_없다_D7() throws Exception {
        long staffId = createUser("staff@example.com", Role.STAFF);
        String token = loginOk("staff@example.com").accessToken();
        createNotice(token).andExpect(status().isCreated());

        jdbc.update("UPDATE users SET role = 'MEMBER' WHERE id = ?", staffId);

        createNotice(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(count("SELECT count(*) FROM post WHERE author_id = ?", staffId)).isEqualTo(1);
        // 세션은 살아 있어 동아리원 API는 계속 통과한다
        callWithToken(ProtectedApi.CREATE_ALBUM, token).andExpect(status().isBadRequest());
    }

    // 공지의 운영진 검사는 서비스의 @PreAuthorize라 입력 검증 뒤에 돈다. 그래서 검증을 통과하는 본문을 보낸다.
    private ResultActions createNotice(String token) throws Exception {
        return mockMvc.perform(
                post("/api/v1/notices")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"정기 세미나\", \"content\": \"동아리방에서 합니다.\"}"));
    }
}
