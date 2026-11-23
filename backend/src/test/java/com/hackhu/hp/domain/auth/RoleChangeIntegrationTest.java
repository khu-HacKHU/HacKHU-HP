package com.hackhu.hp.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.global.auth.Role;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 역할 변경을 실제 토큰, 필터, DB로 확인한다 (4-2-13, 5-2-4, 6-RELEASE-TEST-CASES.md 6-15). */
@ExtendWith(OutputCaptureExtension.class)
class RoleChangeIntegrationTest extends AuthIntegrationTestSupport {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final long LOCK_WAIT_TIMEOUT_MILLIS = 10_000;

    @Autowired DataSource dataSource;

    private long adminId;
    private String adminToken;

    @BeforeEach
    void admin() throws Exception {
        adminId = createUser(ADMIN_EMAIL, Role.ADMIN);
        adminToken = loginOk(ADMIN_EMAIL).accessToken();
    }

    private ResultActions changeRole(String token, long targetId, String role) throws Exception {
        return changeRoleWithBody(token, targetId, "{\"role\": \"%s\"}".formatted(role));
    }

    private ResultActions changeRoleWithBody(String token, long targetId, String body)
            throws Exception {
        return mockMvc.perform(
                put("/api/v1/admin/users/{userId}/role", targetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private String roleOf(long id) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, id);
    }

    private int activeAdmins() {
        return count("SELECT count(*) FROM users WHERE role = 'ADMIN' AND withdrawn_at IS NULL");
    }

    private void withdraw(long id) {
        jdbc.update(
                "UPDATE users SET email = NULL, name = NULL, password_hash = NULL, withdrawn_at = now() WHERE id = ?",
                id);
    }

    @Nested
    class 성공 {

        @ParameterizedTest
        @CsvSource({"MEMBER, STAFF", "MEMBER, ADMIN", "STAFF, MEMBER", "STAFF, ADMIN"})
        void 관리자가_아닌_회원의_역할을_바꾼다_RC1(Role from, Role to) throws Exception {
            long target = createUser("target@example.com", from);

            changeRole(adminToken, target, to.name())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(target))
                    .andExpect(jsonPath("$.email").value("target@example.com"))
                    .andExpect(jsonPath("$.name").value("홍길동"))
                    .andExpect(jsonPath("$.role").value(to.name()))
                    .andExpect(jsonPath("$.createdAt").isNotEmpty())
                    .andExpect(jsonPath("$.approvedAt").isEmpty());

            assertThat(roleOf(target)).isEqualTo(to.name());
        }

        @ParameterizedTest
        @EnumSource(
                value = Role.class,
                names = {"MEMBER", "STAFF"})
        void 다른_활성_관리자가_있으면_관리자의_역할을_내린다_RC2(Role to) throws Exception {
            long other = createUser("other@example.com", Role.ADMIN);

            changeRole(adminToken, other, to.name())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(to.name()));

            assertThat(roleOf(other)).isEqualTo(to.name());
            assertThat(activeAdmins()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(
                value = Role.class,
                names = {"MEMBER", "STAFF", "ADMIN"})
        void 같은_역할이면_행을_바꾸지_않고_200_RC3(Role role) throws Exception {
            long target = createUser("target@example.com", role);
            Instant before =
                    jdbc.queryForObject(
                            "SELECT updated_at FROM users WHERE id = ?", Instant.class, target);

            changeRole(adminToken, target, role.name())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value(role.name()));

            Instant after =
                    jdbc.queryForObject(
                            "SELECT updated_at FROM users WHERE id = ?", Instant.class, target);
            assertThat(after).isEqualTo(before);
        }

        @Test
        void 마지막_관리자가_자기를_관리자로_다시_지정하면_200_RC3() throws Exception {
            changeRole(adminToken, adminId, "ADMIN").andExpect(status().isOk());

            assertThat(roleOf(adminId)).isEqualTo("ADMIN");
        }

        @Test
        void 다른_활성_관리자가_있으면_자기_역할을_내리고_같은_토큰은_바로_권한을_잃는다_RC4() throws Exception {
            long other = createUser("other@example.com", Role.ADMIN);

            changeRole(adminToken, adminId, "MEMBER").andExpect(status().isOk());

            assertThat(roleOf(adminId)).isEqualTo("MEMBER");
            changeRole(adminToken, other, "MEMBER").andExpect(status().isForbidden());
            staffApi(adminToken).andExpect(status().isForbidden());
            memberApi(adminToken).andExpect(status().isOk());
            assertThat(roleOf(other)).isEqualTo("ADMIN");
        }

        @Test
        void 역할을_내리면_다시_로그인하지_않아도_다음_요청부터_403_T6() throws Exception {
            long staff = createUser("staff@example.com", Role.STAFF);
            String staffToken = loginOk("staff@example.com").accessToken();
            staffApi(staffToken).andExpect(status().isOk());

            changeRole(adminToken, staff, "MEMBER").andExpect(status().isOk());

            staffApi(staffToken).andExpect(status().isForbidden());
            memberApi(staffToken).andExpect(status().isOk());
        }

        @Test
        void 역할을_올리면_다시_로그인하지_않아도_다음_요청부터_권한이_생긴다_RC5() throws Exception {
            long member = createUser("member@example.com", Role.MEMBER);
            String memberToken = loginOk("member@example.com").accessToken();
            staffApi(memberToken).andExpect(status().isForbidden());

            changeRole(adminToken, member, "STAFF").andExpect(status().isOk());

            staffApi(memberToken).andExpect(status().isOk());
        }
    }

    @Nested
    class 권한 {

        @Test
        void 로그인하지_않으면_401_RC6() throws Exception {
            long target = createUser("target@example.com", Role.MEMBER);

            mockMvc.perform(
                            put("/api/v1/admin/users/{userId}/role", target)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"role\": \"ADMIN\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

            assertThat(roleOf(target)).isEqualTo("MEMBER");
        }

        @ParameterizedTest
        @EnumSource(
                value = Role.class,
                names = {"PENDING", "MEMBER", "STAFF"})
        void 관리자가_아니면_403이고_자기_역할도_올리지_못한다_RC7(Role role) throws Exception {
            long actor = createUser("actor@example.com", role);
            String token = loginOk("actor@example.com").accessToken();
            long target = createUser("target@example.com", Role.MEMBER);

            changeRole(token, target, "STAFF")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            changeRole(token, actor, "ADMIN").andExpect(status().isForbidden());

            assertThat(roleOf(target)).isEqualTo("MEMBER");
            assertThat(roleOf(actor)).isEqualTo(role.name());
        }
    }

    @Nested
    class 거부 {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "{\"role\": \"PENDING\"}",
                    "{\"role\": \"member\"}",
                    "{\"role\": \"OWNER\"}",
                    "{\"role\": \"\"}",
                    "{\"role\": \"ADMIN\\n\"}",
                    "{\"role\": null}",
                    "{}"
                })
        void 허용하지_않는_역할은_칸별_사유와_400_RC8(String body) throws Exception {
            long target = createUser("target@example.com", Role.MEMBER);

            changeRoleWithBody(adminToken, target, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.errors[0].field").value("role"));

            assertThat(roleOf(target)).isEqualTo("MEMBER");
        }

        @ParameterizedTest
        @ValueSource(strings = {"not-json", "{\"role\": {}}", "{\"role\": [\"ADMIN\"]}"})
        void 깨진_본문은_400이고_500이_아니다_RC8(String body) throws Exception {
            long target = createUser("target@example.com", Role.MEMBER);

            changeRoleWithBody(adminToken, target, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

            assertThat(roleOf(target)).isEqualTo("MEMBER");
        }

        @Test
        void JSON이_아닌_본문은_415_RC8() throws Exception {
            long target = createUser("target@example.com", Role.MEMBER);

            mockMvc.perform(
                            put("/api/v1/admin/users/{userId}/role", target)
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .content("ADMIN"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

            assertThat(roleOf(target)).isEqualTo("MEMBER");
        }

        @Test
        void 없는_회원은_404_RC9() throws Exception {
            changeRole(adminToken, adminId + 1_000, "STAFF")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AUTH_USER_NOT_FOUND"));
        }

        @Test
        void 탈퇴한_회원은_404이고_역할이_바뀌지_않는다_RC9() throws Exception {
            long target = createUser("target@example.com", Role.MEMBER);
            withdraw(target);

            changeRole(adminToken, target, "STAFF")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AUTH_USER_NOT_FOUND"));

            assertThat(roleOf(target)).isEqualTo("MEMBER");
        }

        @ParameterizedTest
        @EnumSource(
                value = Role.class,
                names = {"MEMBER", "STAFF", "ADMIN"})
        void 승인_대기_회원은_409이고_승인을_건너뛰지_못한다_RC10(Role to) throws Exception {
            long target = createUser("pending@example.com", Role.PENDING);

            changeRole(adminToken, target, to.name())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AUTH_TARGET_PENDING"));

            assertThat(roleOf(target)).isEqualTo("PENDING");
            assertThat(
                            count(
                                    "SELECT count(*) FROM users WHERE id = ? AND approved_at IS NOT NULL",
                                    target))
                    .isZero();
        }

        @ParameterizedTest
        @EnumSource(
                value = Role.class,
                names = {"MEMBER", "STAFF"})
        void 마지막_활성_관리자는_내릴_수_없다_RC11(Role to) throws Exception {
            changeRole(adminToken, adminId, to.name())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AUTH_LAST_ADMIN"));

            assertThat(roleOf(adminId)).isEqualTo("ADMIN");
        }

        @Test
        void 탈퇴한_관리자는_활성_관리자로_세지_않는다_RC11() throws Exception {
            long withdrawn = createUser("other@example.com", Role.ADMIN);
            withdraw(withdrawn);

            changeRole(adminToken, adminId, "MEMBER")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AUTH_LAST_ADMIN"));

            assertThat(roleOf(adminId)).isEqualTo("ADMIN");
        }
    }

    @Nested
    class 기록 {

        @Test
        void 성공과_실패를_처리자_대상_역할_결과로_남기고_개인정보는_남기지_않는다_RC13(CapturedOutput output) throws Exception {
            long staff = createUser("staff-log@example.com", Role.STAFF);
            long pending = createUser("pending-log@example.com", Role.PENDING);

            changeRole(adminToken, staff, "MEMBER").andExpect(status().isOk());
            changeRole(adminToken, staff, "MEMBER").andExpect(status().isOk());
            changeRole(adminToken, pending, "MEMBER").andExpect(status().isConflict());
            changeRole(adminToken, adminId, "MEMBER").andExpect(status().isConflict());
            changeRole(adminToken, adminId + 1_000, "MEMBER").andExpect(status().isNotFound());

            assertThat(output)
                    .contains(logLine(staff, "STAFF", "CHANGED"))
                    .contains(logLine(staff, "MEMBER", "UNCHANGED"))
                    .contains(logLine(pending, "PENDING", "TARGET_PENDING"))
                    .contains(logLine(adminId, "ADMIN", "LAST_ADMIN"))
                    .contains(logLine(adminId + 1_000, "null", "NOT_FOUND"))
                    .doesNotContain("staff-log@example.com")
                    .doesNotContain("pending-log@example.com");
        }

        private String logLine(long targetId, String from, String result) {
            return "역할 변경: actorId=%d, targetId=%d, from=%s, to=MEMBER, result=%s"
                    .formatted(adminId, targetId, from, result);
        }
    }

    @Nested
    class 동시성 {

        // 대상 행을 잠가 두 요청이 같은 지점에서 기다리게 한 뒤 함께 풀어 준다. 먼저 끝나는 쪽은 매번 달라진다.
        @RepeatedTest(5)
        void 활성_관리자_둘이_서로를_내리면_하나만_성공한다_CC6() throws Exception {
            long other = createUser("other@example.com", Role.ADMIN);
            String otherToken = loginOk("other@example.com").accessToken();

            List<MvcResult> results;
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                List<Future<MvcResult>> futures = new ArrayList<>();
                try (Connection holder = dataSource.getConnection()) {
                    holder.setAutoCommit(false);
                    lockRow(holder, Math.min(adminId, other));
                    futures.add(
                            executor.submit(
                                    () -> changeRole(adminToken, other, "MEMBER").andReturn()));
                    futures.add(
                            executor.submit(
                                    () -> changeRole(otherToken, adminId, "MEMBER").andReturn()));
                    awaitBlockedBackends(2);
                    holder.commit();
                }
                results = collect(futures);
            }

            assertThat(results)
                    .extracting(r -> r.getResponse().getStatus())
                    .containsExactlyInAnyOrder(200, 409);
            MvcResult rejected =
                    results.stream()
                            .filter(r -> r.getResponse().getStatus() == 409)
                            .findFirst()
                            .orElseThrow();
            assertThat(rejected.getResponse().getContentAsString()).contains("AUTH_LAST_ADMIN");
            assertThat(activeAdmins()).isEqualTo(1);
        }

        @RepeatedTest(5)
        void 승격과_강등이_겹쳐도_둘_다_처리되고_활성_관리자가_남는다_CC11() throws Exception {
            long otherAdmin = createUser("other@example.com", Role.ADMIN);
            String otherToken = loginOk("other@example.com").accessToken();
            long target = createUser("target@example.com", Role.STAFF);

            List<MvcResult> results;
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                List<Future<MvcResult>> futures = new ArrayList<>();
                try (Connection holder = dataSource.getConnection()) {
                    holder.setAutoCommit(false);
                    lockRow(holder, target);
                    futures.add(
                            executor.submit(
                                    () -> changeRole(adminToken, target, "ADMIN").andReturn()));
                    futures.add(
                            executor.submit(
                                    () -> changeRole(otherToken, target, "MEMBER").andReturn()));
                    awaitBlockedBackends(2);
                    holder.commit();
                }
                results = collect(futures);
            }

            assertThat(results).allMatch(r -> r.getResponse().getStatus() == 200);
            assertThat(roleOf(target)).isIn("ADMIN", "MEMBER");
            assertThat(roleOf(adminId)).isEqualTo("ADMIN");
            assertThat(roleOf(otherAdmin)).isEqualTo("ADMIN");
        }

        @Test
        void 강등_도중_대상이_관리자가_되고_처리자가_내려가면_마지막_관리자로_409_CC11() throws Exception {
            long target = createUser("target@example.com", Role.STAFF);

            MvcResult result;
            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                Future<MvcResult> future;
                try (Connection holder = dataSource.getConnection()) {
                    holder.setAutoCommit(false);
                    setRole(holder, target, "ADMIN");
                    setRole(holder, adminId, "MEMBER");
                    future =
                            executor.submit(
                                    () -> changeRole(adminToken, target, "MEMBER").andReturn());
                    awaitBlockedBackends(1);
                    holder.commit();
                }
                result = future.get(30, TimeUnit.SECONDS);
            }

            assertThat(result.getResponse().getStatus()).isEqualTo(409);
            assertThat(result.getResponse().getContentAsString()).contains("AUTH_LAST_ADMIN");
            assertThat(roleOf(target)).isEqualTo("ADMIN");
            assertThat(activeAdmins()).isEqualTo(1);
        }

        @Test
        void 관리자_잠금을_기다리는_동안_대상이_내려가면_비관리자_경로로_처리한다_RC12() throws Exception {
            long target = createUser("target@example.com", Role.ADMIN);

            MvcResult result;
            try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
                Future<MvcResult> future;
                try (Connection holder = dataSource.getConnection()) {
                    holder.setAutoCommit(false);
                    setRole(holder, target, "STAFF");
                    future =
                            executor.submit(
                                    () -> changeRole(adminToken, target, "MEMBER").andReturn());
                    awaitBlockedBackends(1);
                    holder.commit();
                }
                result = future.get(30, TimeUnit.SECONDS);
            }

            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            assertThat(roleOf(target)).isEqualTo("MEMBER");
            assertThat(activeAdmins()).isEqualTo(1);
        }

        private List<MvcResult> collect(List<Future<MvcResult>> futures) throws Exception {
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        }

        private void lockRow(Connection connection, long id) throws Exception {
            try (PreparedStatement statement =
                    connection.prepareStatement("SELECT id FROM users WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, id);
                try (ResultSet ignored = statement.executeQuery()) {
                    // 잠금만 잡는다.
                }
            }
        }

        private void setRole(Connection connection, long id, String role) throws Exception {
            try (PreparedStatement statement =
                    connection.prepareStatement("UPDATE users SET role = ? WHERE id = ?")) {
                statement.setString(1, role);
                statement.setLong(2, id);
                statement.executeUpdate();
            }
        }

        // 요청들이 행 잠금에서 실제로 기다릴 때까지 본다. 시간으로 맞추지 않아 순서가 흔들리지 않는다.
        private void awaitBlockedBackends(int expected) throws Exception {
            long deadline = System.currentTimeMillis() + LOCK_WAIT_TIMEOUT_MILLIS;
            while (System.currentTimeMillis() < deadline) {
                int blocked =
                        count(
                                """
                                SELECT count(*)
                                  FROM pg_stat_activity
                                 WHERE datname = current_database()
                                   AND wait_event_type = 'Lock'
                                """);
                if (blocked >= expected) {
                    return;
                }
                Thread.sleep(10);
            }
            throw new AssertionError("요청이 잠금에서 기다리지 않음: " + expected);
        }
    }
}
