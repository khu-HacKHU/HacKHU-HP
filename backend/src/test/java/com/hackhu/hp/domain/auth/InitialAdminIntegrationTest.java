package com.hackhu.hp.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.domain.user.service.InitialAdminProperties;
import com.hackhu.hp.domain.user.service.InitialAdminService;
import com.hackhu.hp.domain.user.service.InitialAdminService.Outcome;
import com.hackhu.hp.global.auth.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 처음 관리자 생성(#23)을 실제 DB로 확인한다. Docker가 필요하다.
 *
 * <p>생성 코드는 {@code domain/user}에 있지만, 만든 관리자로 로그인까지 확인하려고 로그인 테스트와 같은 준비를 쓴다. 같은 Spring 컨텍스트와
 * PostgreSQL 컨테이너를 함께 쓴다.
 */
@ExtendWith(OutputCaptureExtension.class)
class InitialAdminIntegrationTest extends AuthIntegrationTestSupport {

    private static final String EMAIL = "admin@example.com";
    private static final String NAME = "홍길동";

    @Autowired InitialAdminService initialAdminService;

    private static InitialAdminProperties values(String email, String name, String password) {
        return new InitialAdminProperties(email, name, password, true);
    }

    private long activeAdminCount() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE role = 'ADMIN' AND withdrawn_at IS NULL",
                Long.class);
    }

    private long userCount() {
        return jdbc.queryForObject("SELECT count(*) FROM users", Long.class);
    }

    @Test
    void 빈_DB에서_관리자를_비밀번호_변경_필요_상태로_만든다_IA1(CapturedOutput output) {
        Outcome outcome =
                initialAdminService.createIfNoActiveAdmin(
                        values("  Admin@Example.COM ", "  " + NAME + " ", PASSWORD));

        assertThat(outcome).isEqualTo(Outcome.CREATED);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM users");
        assertThat(row.get("email")).isEqualTo(EMAIL);
        assertThat(row.get("name")).isEqualTo(NAME);
        assertThat(row.get("role")).isEqualTo("ADMIN");
        assertThat(row.get("must_change_password")).isEqualTo(true);
        // 임시 비밀번호가 아니므로 일반 로그인 경로를 쓴다 (5-2-3).
        assertThat(row.get("temp_password_expires_at")).isNull();
        assertThat(row.get("approved_at")).isNull();
        String hash = (String) row.get("password_hash");
        assertThat(hash).startsWith("$2a$12$").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
        // 비밀번호와 개인정보는 로그에 남지 않는다 (CWE-532).
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContainIgnoringCase(EMAIL);
    }

    @Test
    void 만든_관리자는_로그인하면_비밀번호부터_바꿔야_한다_IA2() throws Exception {
        initialAdminService.createIfNoActiveAdmin(values(EMAIL, NAME, PASSWORD));

        MvcResult result =
                login(EMAIL, PASSWORD)
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.user.role").value("ADMIN"))
                        .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                        .andReturn();

        staffApi(accessTokenOf(result))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void 활성_관리자가_있으면_만들지_않는다_IA3(CapturedOutput output) {
        createUser("first@example.com", Role.ADMIN);

        Outcome outcome = initialAdminService.createIfNoActiveAdmin(values(EMAIL, NAME, PASSWORD));

        assertThat(outcome).isEqualTo(Outcome.ADMIN_EXISTS);
        assertThat(userCount()).isEqualTo(1);
        // 값이 남아 있으면 지우라고 알린다.
        assertThat(output.getAll()).contains("시크릿에서 지우세요");
    }

    @Test
    void 탈퇴한_관리자는_세지_않는다_IA4() {
        jdbc.update("INSERT INTO users (role, withdrawn_at) VALUES ('ADMIN', now())");

        Outcome outcome = initialAdminService.createIfNoActiveAdmin(values(EMAIL, NAME, PASSWORD));

        assertThat(outcome).isEqualTo(Outcome.CREATED);
        assertThat(activeAdminCount()).isEqualTo(1);
    }

    @Test
    void 관리자도_값도_없으면_서버를_멈춘다_IA5() {
        assertThatThrownBy(() -> initialAdminService.createIfNoActiveAdmin(values("", "", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INITIAL_ADMIN_PASSWORD");
        assertThat(userCount()).isZero();
    }

    @Test
    void local처럼_필수가_아니면_값이_없어도_경고만_남긴다_IA6(CapturedOutput output) {
        Outcome outcome =
                initialAdminService.createIfNoActiveAdmin(
                        new InitialAdminProperties(null, null, null, false));

        assertThat(outcome).isEqualTo(Outcome.NOT_CONFIGURED);
        assertThat(userCount()).isZero();
        assertThat(output.getAll()).contains("처음 관리자를 만들지 않았습니다");
    }

    @Test
    void 값이_일부만_있으면_필수가_아니어도_서버를_멈춘다_IA7() {
        InitialAdminProperties partial = new InitialAdminProperties(EMAIL, NAME, "", false);

        assertThatThrownBy(() -> initialAdminService.createIfNoActiveAdmin(partial))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INITIAL_ADMIN_PASSWORD")
                .message()
                .doesNotContain(EMAIL)
                .doesNotContain(NAME);
        assertThat(userCount()).isZero();
    }

    @ParameterizedTest(name = "{3}")
    @CsvSource(
            delimiter = '|',
            value = {
                "admin@example.com | 홍길동 | short | 비밀번호가 10자보다 짧음 | INITIAL_ADMIN_PASSWORD",
                "admin@example.com | 홍길동 | admin@example.com | 비밀번호가 이메일과 같음 | INITIAL_ADMIN_PASSWORD",
                "홍길동@example.com | 홍길동 | correct-horse-battery | ASCII가 아닌 이메일 | INITIAL_ADMIN_EMAIL",
                "not-an-email | 홍길동 | correct-horse-battery | 이메일 형식이 아님 | INITIAL_ADMIN_EMAIL",
                "admin@example.com | 홍\u202E길동 | correct-horse-battery | 이름에 양방향 제어 문자 | INITIAL_ADMIN_NAME"
            })
    void 가입과_같은_규칙에_맞지_않으면_서버를_멈춘다_IA8(
            String email, String name, String password, String reason, String envName) {
        assertThatThrownBy(() ->
                        initialAdminService.createIfNoActiveAdmin(values(email, name, password)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(envName)
                .message()
                .doesNotContain(email)
                .doesNotContain(password);
        assertThat(userCount()).isZero();
    }

    @Test
    void 같은_이메일의_계정이_있으면_올리지_않고_서버를_멈춘다_IA9() {
        // 누가 관리자 이메일로 먼저 가입한 경우. 그 계정을 관리자로 올리면 관리자 자리를 빼앗긴다 (DECISIONS.md A10).
        long id = createUser(EMAIL, Role.PENDING);

        assertThatThrownBy(() ->
                        initialAdminService.createIfNoActiveAdmin(values(EMAIL, NAME, PASSWORD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INITIAL_ADMIN_EMAIL")
                .message()
                .doesNotContain(EMAIL);
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");
        assertThat(activeAdminCount()).isZero();
    }

    @Test
    void 서버_두_대가_동시에_시작해도_관리자는_한_명이다_CC9() throws Exception {
        // 이메일을 다르게 해서 유니크 인덱스가 아니라 잠금이 막는지 본다.
        List<InitialAdminProperties> servers =
                List.of(
                        values("server-a@example.com", NAME, PASSWORD),
                        values("server-b@example.com", NAME, PASSWORD));
        CountDownLatch start = new CountDownLatch(1);
        List<Outcome> outcomes = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(servers.size())) {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (InitialAdminProperties server : servers) {
                futures.add(
                        executor.submit(() -> {
                            start.await();
                            return initialAdminService.createIfNoActiveAdmin(server);
                        }));
            }
            start.countDown();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get());
            }
        }

        assertThat(outcomes).containsExactlyInAnyOrder(Outcome.CREATED, Outcome.ADMIN_EXISTS);
        assertThat(activeAdminCount()).isEqualTo(1);
    }
}
