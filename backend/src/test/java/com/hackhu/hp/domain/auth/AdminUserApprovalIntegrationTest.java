package com.hackhu.hp.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.global.auth.Role;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 회원 목록, 가입 승인, 가입 거절을 실제 토큰, 필터, DB로 확인한다 (#45, 6-RELEASE-TEST-CASES.md 6-14). */
@ExtendWith(OutputCaptureExtension.class)
class AdminUserApprovalIntegrationTest extends AuthIntegrationTestSupport {

    private static final String USERS = "/api/v1/admin/users";

    private String tokenOf(String email, Role role) throws Exception {
        createUser(email, role);
        return loginOk(email).accessToken();
    }

    private long createPending(String email) {
        return createUser(email, Role.PENDING);
    }

    private long createWithdrawn(Role role) {
        return jdbc.queryForObject(
                "INSERT INTO users (role, withdrawn_at) VALUES (?, now()) RETURNING id",
                Long.class,
                role.name());
    }

    private static MockHttpServletRequestBuilder bearer(
            MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private ResultActions list(String token, String... query) throws Exception {
        MockHttpServletRequestBuilder request = bearer(get(USERS), token);
        for (int i = 0; i < query.length; i += 2) {
            request.param(query[i], query[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private ResultActions approve(String token, long userId) throws Exception {
        return mockMvc.perform(bearer(post(USERS + "/" + userId + "/approve"), token));
    }

    private ResultActions reject(String token, long userId) throws Exception {
        return mockMvc.perform(bearer(post(USERS + "/" + userId + "/reject"), token));
    }

    private String roleOf(long userId) {
        return jdbc.queryForObject("SELECT role FROM users WHERE id = ?", String.class, userId);
    }

    private int rowsOf(long userId) {
        return count("SELECT count(*) FROM users WHERE id = ?", userId);
    }

    // 가입 순서를 시각으로 고정한다. 한 트랜잭션의 now()가 같아 정렬이 ID에 기대지 않게 한다.
    private void setCreatedAt(long userId, String timestamp) {
        jdbc.update(
                "UPDATE users SET created_at = CAST(? AS timestamptz) WHERE id = ?",
                timestamp,
                userId);
    }

    @ParameterizedTest
    @EnumSource(
            value = Role.class,
            names = {"STAFF", "ADMIN"})
    void 운영진과_관리자는_목록을_보고_승인하고_거절한다_AP1(Role role) throws Exception {
        String token = tokenOf("staff@example.com", role);
        long toApprove = createPending("approve@example.com");
        long toReject = createPending("reject@example.com");

        list(token, "role", "PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        approve(token, toApprove).andExpect(status().isOk());
        reject(token, toReject).andExpect(status().isNoContent());

        assertThat(roleOf(toApprove)).isEqualTo("MEMBER");
        assertThat(rowsOf(toReject)).isZero();
    }

    @Test
    void 목록은_약속한_필드만_주고_탈퇴한_계정을_뺀다_AP2() throws Exception {
        String token = tokenOf("staff@example.com", Role.STAFF);
        long pending = createPending("newbie@example.com");
        createWithdrawn(Role.PENDING);

        list(token, "role", "PENDING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pending))
                .andExpect(jsonPath("$.content[0].email").value("newbie@example.com"))
                .andExpect(jsonPath("$.content[0].name").value("홍길동"))
                .andExpect(jsonPath("$.content[0].role").value("PENDING"))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].approvedAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].mustChangePassword").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void 역할을_주지_않으면_모든_역할을_보고_주면_그_역할만_본다_AP3() throws Exception {
        String token = tokenOf("staff@example.com", Role.STAFF);
        createPending("pending@example.com");
        createUser("member@example.com", Role.MEMBER);

        list(token).andExpect(jsonPath("$.totalElements").value(3));
        list(token, "role", "MEMBER")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("member@example.com"));
    }

    @Test
    void 기본은_최근_가입순이고_오름차순도_되며_같은_시각은_ID로_정렬한다_AP4() throws Exception {
        String token = tokenOf("staff@example.com", Role.STAFF);
        long first = createPending("first@example.com");
        long second = createPending("second@example.com");
        long third = createPending("third@example.com");
        setCreatedAt(first, "2026-11-01T00:00:00Z");
        setCreatedAt(second, "2026-11-02T00:00:00Z");
        setCreatedAt(third, "2026-11-02T00:00:00Z");

        list(token, "role", "PENDING")
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[2].id").value(first));
        list(token, "role", "PENDING", "sort", "createdAt,asc")
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[2].id").value(third));
    }

    @Test
    void size가_100을_넘으면_100으로_맞춘다_AP5() throws Exception {
        String token = tokenOf("staff@example.com", Role.STAFF);

        list(token, "size", "101").andExpect(jsonPath("$.size").value(100));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"email,asc", "passwordHash", "createdAt,desc,ignorecase", "createdAt,id"})
    void 허용하지_않은_정렬은_400_AP6(String sort) throws Exception {
        String token = tokenOf("staff@example.com", Role.STAFF);

        list(token, "sort", sort)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 없는_역할은_400_AP6() throws Exception {
        String token = tokenOf("staff@example.com", Role.STAFF);

        list(token, "role", "OWNER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 승인하면_동아리원이_되고_같은_토큰으로_동아리원_API를_부른다_AP7() throws Exception {
        String staff = tokenOf("staff@example.com", Role.STAFF);
        long pending = createPending("newbie@example.com");
        String pendingToken = loginOk("newbie@example.com").accessToken();
        memberApi(pendingToken).andExpect(status().isForbidden());

        approve(staff, pending)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pending))
                .andExpect(jsonPath("$.email").value("newbie@example.com"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.approvedAt").isNotEmpty());

        memberApi(pendingToken).andExpect(status().isOk());
    }

    @Test
    void 거절하면_세션이_사라지고_기존_토큰은_401이다_AP8() throws Exception {
        String staff = tokenOf("staff@example.com", Role.STAFF);
        long pending = createPending("newbie@example.com");
        LoggedIn rejected = loginOk("newbie@example.com");

        reject(staff, pending).andExpect(status().isNoContent());

        assertThat(rowsOf(pending)).isZero();
        assertThat(count("SELECT count(*) FROM login_sessions WHERE user_id = ?", pending))
                .isZero();
        memberApi(rejected.accessToken()).andExpect(status().isUnauthorized());
        refresh(rejected.refreshCookie()).andExpect(status().isUnauthorized());
    }

    @Test
    void 거절된_이메일로_다시_가입할_수_있다_AP9() throws Exception {
        String staff = tokenOf("staff@example.com", Role.STAFF);
        long pending = createPending("newbie@example.com");
        reject(staff, pending).andExpect(status().isNoContent());

        mockMvc.perform(
                        fromIp(
                                post("/api/v1/auth/signup")
                                        .header(HttpHeaders.ORIGIN, FRONTEND)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"email": "newbie@example.com", "password": "%s", "name": "홍길동"}
                                                """
                                                        .formatted(PASSWORD)),
                                ip))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PENDING"));
    }

    @ParameterizedTest
    @EnumSource(
            value = Role.class,
            names = {"MEMBER", "STAFF", "ADMIN"})
    void 승인_대기가_아니면_승인과_거절_모두_409이고_바뀌지_않는다_AP10(Role targetRole) throws Exception {
        String staff = tokenOf("staff@example.com", Role.STAFF);
        long target = createUser("target@example.com", targetRole);

        approve(staff, target)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_USER_NOT_PENDING"));
        reject(staff, target)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_USER_NOT_PENDING"));

        assertThat(roleOf(target)).isEqualTo(targetRole.name());
    }

    @Test
    void 없는_회원과_탈퇴한_회원은_404_AP11() throws Exception {
        String staff = tokenOf("staff@example.com", Role.STAFF);
        long withdrawn = createWithdrawn(Role.PENDING);
        long missing = withdrawn + 1000;

        for (long target : List.of(missing, withdrawn)) {
            approve(staff, target)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AUTH_USER_NOT_FOUND"));
            reject(staff, target)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("AUTH_USER_NOT_FOUND"));
        }
        assertThat(roleOf(withdrawn)).isEqualTo("PENDING");
    }

    @Test
    void 로그인하지_않으면_401이고_바뀌지_않는다_AP12() throws Exception {
        long pending = createPending("newbie@example.com");

        mockMvc.perform(get(USERS)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(USERS + "/" + pending + "/approve"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(USERS + "/" + pending + "/reject"))
                .andExpect(status().isUnauthorized());

        assertThat(roleOf(pending)).isEqualTo("PENDING");
    }

    @ParameterizedTest
    @EnumSource(
            value = Role.class,
            names = {"PENDING", "MEMBER"})
    void 운영진이_아니면_403이고_바뀌지_않는다_AP13(Role role) throws Exception {
        String token = tokenOf("actor@example.com", role);
        long pending = createPending("newbie@example.com");

        list(token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        approve(token, pending).andExpect(status().isForbidden());
        reject(token, pending).andExpect(status().isForbidden());

        assertThat(roleOf(pending)).isEqualTo("PENDING");
    }

    @Test
    void 승인_대기_회원은_자기_자신을_승인할_수_없다_AP13() throws Exception {
        long self = createPending("self@example.com");
        String token = loginOk("self@example.com").accessToken();

        approve(token, self).andExpect(status().isForbidden());

        assertThat(roleOf(self)).isEqualTo("PENDING");
    }

    @Test
    void 처리_기록에_처리자와_대상과_결과만_남긴다_AP14(CapturedOutput output) throws Exception {
        createUser("staff@example.com", Role.STAFF);
        long staffId =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE email = 'staff@example.com'", Long.class);
        String staff = loginOk("staff@example.com").accessToken();
        long approved = createPending("approved-target@example.com");
        long rejected = createPending("rejected-target@example.com");

        approve(staff, approved).andExpect(status().isOk());
        approve(staff, approved).andExpect(status().isConflict());
        reject(staff, rejected).andExpect(status().isNoContent());
        reject(staff, rejected).andExpect(status().isNotFound());

        assertThat(output)
                .contains(
                        "가입 승인: actorId=%d, targetId=%d, result=SUCCESS"
                                .formatted(staffId, approved))
                .contains(
                        "가입 승인: actorId=%d, targetId=%d, result=AUTH_USER_NOT_PENDING"
                                .formatted(staffId, approved))
                .contains(
                        "가입 거절: actorId=%d, targetId=%d, result=SUCCESS"
                                .formatted(staffId, rejected))
                .contains(
                        "가입 거절: actorId=%d, targetId=%d, result=AUTH_USER_NOT_FOUND"
                                .formatted(staffId, rejected))
                .doesNotContain("approved-target@example.com")
                .doesNotContain("rejected-target@example.com");
    }

    @Test
    void 승인한_뒤_거절은_409이고_거절한_뒤_승인은_404다_CC5() throws Exception {
        String staff = tokenOf("staff@example.com", Role.STAFF);
        long approvedFirst = createPending("approved@example.com");
        long rejectedFirst = createPending("rejected@example.com");

        approve(staff, approvedFirst).andExpect(status().isOk());
        reject(staff, approvedFirst).andExpect(status().isConflict());
        assertThat(roleOf(approvedFirst)).isEqualTo("MEMBER");

        reject(staff, rejectedFirst).andExpect(status().isNoContent());
        approve(staff, rejectedFirst).andExpect(status().isNotFound());
        assertThat(rowsOf(rejectedFirst)).isZero();
    }

    @Test
    void 승인과_거절을_동시에_보내면_하나만_성공한다_CC5() throws Exception {
        String staff = tokenOf("staff@example.com", Role.STAFF);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 20; round++) {
                long target = createPending("race" + round + "@example.com");
                CountDownLatch start = new CountDownLatch(2);

                Future<Integer> approved =
                        executor.submit(raceCall(start, () -> approve(staff, target)));
                Future<Integer> rejected =
                        executor.submit(raceCall(start, () -> reject(staff, target)));
                int approveStatus = approved.get(30, TimeUnit.SECONDS);
                int rejectStatus = rejected.get(30, TimeUnit.SECONDS);

                if (approveStatus == 200) {
                    assertThat(rejectStatus).isEqualTo(409);
                    assertThat(roleOf(target)).isEqualTo("MEMBER");
                } else {
                    assertThat(approveStatus).isEqualTo(404);
                    assertThat(rejectStatus).isEqualTo(204);
                    assertThat(rowsOf(target)).isZero();
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<Integer> raceCall(CountDownLatch start, Callable<ResultActions> call) {
        return () -> {
            start.countDown();
            start.await(10, TimeUnit.SECONDS);
            return call.call().andReturn().getResponse().getStatus();
        };
    }
}
