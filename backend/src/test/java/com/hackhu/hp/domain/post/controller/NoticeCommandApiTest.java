package com.hackhu.hp.domain.post.controller;

import static com.hackhu.hp.global.auth.LoginUserRequests.loginUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.TestcontainersConfiguration;
import com.hackhu.hp.domain.post.service.AuthorNameReader;
import com.hackhu.hp.global.auth.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 공지 작성, 수정, 삭제 API (#32). 권한 거부 케이스를 성공 케이스보다 먼저 둔다 (code-style.md "테스트").
 *
 * <p>역할은 {@code loginUser(id, role)}로 준다. 실제 요청에서는 인증 필터가 DB에서 역할을 읽는다 (auth A2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class NoticeCommandApiTest {

    private static final long AUTHOR = 9101L;
    private static final long OTHER_STAFF = 9102L;
    private static final long ADMIN = 9103L;
    private static final Instant BASE = Instant.parse("2026-11-07T09:30:00Z");
    private static final String BODY = "{\"title\":\"세미나 안내\",\"content\":\"본문\"}";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean AuthorNameReader authorNameReader;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM post");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (9101, 9102, 9103)");
        saveUser(AUTHOR, "STAFF");
        saveUser(OTHER_STAFF, "STAFF");
        saveUser(ADMIN, "ADMIN");
        given(authorNameReader.findNames(anyCollection()))
                .willReturn(Map.of(AUTHOR, "작성자", ADMIN, "관리자"));
    }

    // 작성 권한

    @Test
    void 로그인하지_않고_쓰면_401이다() throws Exception {
        mockMvc.perform(json(post("/api/v1/notices"), BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        assertThat(postCount()).isZero();
    }

    @Test
    void 승인_대기_회원과_동아리원은_공지를_쓸_수_없다() throws Exception {
        for (Role role : new Role[] {Role.PENDING, Role.MEMBER}) {
            mockMvc.perform(json(post("/api/v1/notices"), BODY).with(loginUser(AUTHOR, role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        assertThat(postCount()).isZero();
    }

    @Test
    void 운영진이_공지를_쓰면_201과_Location을_준다() throws Exception {
        mockMvc.perform(json(post("/api/v1/notices"), BODY).with(as(AUTHOR, Role.STAFF)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/notices/\\d+")))
                .andExpect(jsonPath("$.title").value("세미나 안내"))
                .andExpect(jsonPath("$.content").value("본문"))
                .andExpect(jsonPath("$.author.id").value(AUTHOR))
                .andExpect(jsonPath("$.author.name").value("작성자"));
        assertThat(jdbcTemplate.queryForObject("SELECT board_type FROM post", String.class))
                .isEqualTo("NOTICE");
    }

    @Test
    void 작성자와_게시판_종류는_요청으로_바꿀_수_없다() throws Exception {
        String body =
                "{\"title\":\"제목\",\"content\":\"본문\",\"authorId\":9103,\"boardType\":\"FREE\","
                        + "\"id\":1}";

        mockMvc.perform(json(post("/api/v1/notices"), body).with(as(AUTHOR, Role.STAFF)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.id").value(AUTHOR));
        assertThat(jdbcTemplate.queryForObject("SELECT author_id FROM post", Long.class))
                .isEqualTo(AUTHOR);
        assertThat(jdbcTemplate.queryForObject("SELECT board_type FROM post", String.class))
                .isEqualTo("NOTICE");
    }

    @Test
    void JSON이_아니면_415다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/notices")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("제목")
                                .with(as(AUTHOR, Role.STAFF)))
                .andExpect(status().isUnsupportedMediaType());
    }

    // 입력 검증

    @Test
    void 제목은_앞뒤_공백을_지우고_본문_줄바꿈은_LF로_맞춘다() throws Exception {
        String body = "{\"title\":\"  세미나  \",\"content\":\"첫 줄\\r\\n둘째 줄\\r셋째 줄\"}";

        mockMvc.perform(json(post("/api/v1/notices"), body).with(as(AUTHOR, Role.STAFF)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("세미나"))
                .andExpect(jsonPath("$.content").value("첫 줄\n둘째 줄\n셋째 줄"));
    }

    @Test
    void 제목과_본문이_비면_400과_필드별_사유를_준다() throws Exception {
        mockMvc.perform(
                        json(post("/api/v1/notices"), "{\"title\":\"   \",\"content\":\"\\n\"}")
                                .with(as(AUTHOR, Role.STAFF)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'content')]").exists());
    }

    @Test
    void 제목은_코드_포인트로_100자까지다() throws Exception {
        String emoji100 = "😀".repeat(100);
        String title101 = "가".repeat(101);

        mockMvc.perform(write(emoji100, "본문")).andExpect(status().isCreated());
        mockMvc.perform(write(title101, "본문"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void 본문은_10000자까지다() throws Exception {
        mockMvc.perform(write("제목", "가".repeat(10_000))).andExpect(status().isCreated());
        mockMvc.perform(write("제목", "가".repeat(10_001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("content"));
    }

    @Test
    void 제목의_줄바꿈과_양방향_제어_문자는_거부한다() throws Exception {
        for (String title : new String[] {"첫줄\\n둘째줄", "공지\\u202Etxt.exe", "a\\u2066b"}) {
            mockMvc.perform(write(title, "본문"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("title"));
        }
    }

    @Test
    void 본문의_NUL_문자는_거부하고_탭과_줄바꿈은_받는다() throws Exception {
        mockMvc.perform(write("제목", "a\\u0000b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("content"));
        mockMvc.perform(write("제목", "a\\tb\\nc")).andExpect(status().isCreated());
    }

    // 수정, 삭제 권한

    @Test
    void 다른_운영진의_공지는_고치거나_지울_수_없다() throws Exception {
        long id = saveNotice(AUTHOR);

        mockMvc.perform(
                        json(put("/api/v1/notices/{id}", id), BODY)
                                .with(as(OTHER_STAFF, Role.STAFF)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_FORBIDDEN"));
        mockMvc.perform(delete("/api/v1/notices/{id}", id).with(as(OTHER_STAFF, Role.STAFF)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_FORBIDDEN"));
        assertThat(titleOf(id)).isEqualTo("원래 제목");
    }

    @Test
    void 동아리원으로_내려간_작성자는_자기_공지도_고칠_수_없다() throws Exception {
        long id = saveNotice(AUTHOR);

        mockMvc.perform(json(put("/api/v1/notices/{id}", id), BODY).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/v1/notices/{id}", id).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(titleOf(id)).isEqualTo("원래 제목");
    }

    @Test
    void 로그인하지_않고_고치거나_지우면_401이다() throws Exception {
        long id = saveNotice(AUTHOR);

        mockMvc.perform(json(put("/api/v1/notices/{id}", id), BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/notices/{id}", id)).andExpect(status().isUnauthorized());
    }

    @Test
    void 운영진이_아니면_없는_공지라도_404가_아니라_403이다() throws Exception {
        mockMvc.perform(
                        json(put("/api/v1/notices/{id}", 999_999), BODY)
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void 없는_공지를_고치거나_지우면_404다() throws Exception {
        mockMvc.perform(
                        json(put("/api/v1/notices/{id}", 999_999), BODY)
                                .with(as(AUTHOR, Role.STAFF)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/notices/{id}", 999_999).with(as(AUTHOR, Role.STAFF)))
                .andExpect(status().isNotFound());
    }

    // 수정, 삭제 성공

    @Test
    void 작성자가_공지를_고치면_updatedAt이_늦어지고_작성자와_작성_시각은_그대로다() throws Exception {
        long id = saveNotice(AUTHOR);

        mockMvc.perform(
                        json(
                                        put("/api/v1/notices/{id}", id),
                                        "{\"title\":\"고친 제목\",\"content\":\"고친 본문\"}")
                                .with(as(AUTHOR, Role.STAFF)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("고친 제목"))
                .andExpect(jsonPath("$.content").value("고친 본문"))
                .andExpect(jsonPath("$.author.id").value(AUTHOR))
                .andExpect(jsonPath("$.createdAt").value("2026-11-07T09:30:00Z"));
        Instant updatedAt =
                jdbcTemplate
                        .queryForObject(
                                "SELECT updated_at FROM post WHERE id = ?", Timestamp.class, id)
                        .toInstant();
        assertThat(updatedAt).isAfter(BASE);
    }

    @Test
    void 관리자는_남의_공지를_고쳐도_작성자가_바뀌지_않는다() throws Exception {
        long id = saveNotice(AUTHOR);

        mockMvc.perform(json(put("/api/v1/notices/{id}", id), BODY).with(as(ADMIN, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.id").value(AUTHOR));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT author_id FROM post WHERE id = ?", Long.class, id))
                .isEqualTo(AUTHOR);
    }

    @Test
    void 작성자가_공지를_지우면_204이고_다시_보면_404다() throws Exception {
        long id = saveNotice(AUTHOR);

        mockMvc.perform(delete("/api/v1/notices/{id}", id).with(as(AUTHOR, Role.STAFF)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/notices/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/notices/{id}", id).with(as(AUTHOR, Role.STAFF)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 관리자는_남의_공지를_지울_수_있다() throws Exception {
        long id = saveNotice(AUTHOR);

        mockMvc.perform(delete("/api/v1/notices/{id}", id).with(as(ADMIN, Role.ADMIN)))
                .andExpect(status().isNoContent());
        assertThat(postCount()).isZero();
    }

    // 관리자 작업 기록

    @Test
    void 관리자가_남의_공지를_고치거나_지우면_기록을_남긴다(CapturedOutput output) throws Exception {
        long id = saveNotice(AUTHOR);

        mockMvc.perform(json(put("/api/v1/notices/{id}", id), BODY).with(as(ADMIN, Role.ADMIN)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/notices/{id}", id).with(as(ADMIN, Role.ADMIN)))
                .andExpect(status().isNoContent());

        assertThat(output.getOut())
                .contains("관리자가 남의 공지를 수정함 adminId=" + ADMIN + " postId=" + id)
                .contains("관리자가 남의 공지를 삭제함 adminId=" + ADMIN + " postId=" + id)
                // 제목과 본문은 남기지 않는다 (CWE-532)
                .doesNotContain("세미나 안내");
    }

    @Test
    void 거부된_요청과_자기_공지는_관리자_기록을_남기지_않는다(CapturedOutput output) throws Exception {
        long id = saveNotice(AUTHOR);

        mockMvc.perform(
                        json(put("/api/v1/notices/{id}", id), BODY)
                                .with(as(OTHER_STAFF, Role.STAFF)))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/v1/notices/{id}", id), BODY).with(as(AUTHOR, Role.STAFF)))
                .andExpect(status().isOk());

        assertThat(output.getOut()).doesNotContain("관리자가 남의 공지를");
    }

    @Test
    void 반영에_실패한_관리자_수정은_기록하지_않는다(CapturedOutput output) throws Exception {
        long id = saveNotice(AUTHOR);
        // 애플리케이션 검사를 지나도 DB 제약에 걸리게 만든다. 반영에 실패하면 기록이 없어야 한다 (PR #114 리뷰).
        jdbcTemplate.execute(
                "ALTER TABLE post ADD CONSTRAINT ck_test_title CHECK (title <> '막힐 제목')");
        try {
            mockMvc.perform(
                            json(
                                            put("/api/v1/notices/{id}", id),
                                            "{\"title\":\"막힐 제목\",\"content\":\"본문\"}")
                                    .with(as(ADMIN, Role.ADMIN)))
                    .andExpect(status().is5xxServerError());
        } finally {
            jdbcTemplate.execute("ALTER TABLE post DROP CONSTRAINT ck_test_title");
        }

        assertThat(output.getOut()).doesNotContain("관리자가 남의 공지를 수정함");
        assertThat(titleOf(id)).isEqualTo("원래 제목");
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // 제목과 본문은 JSON 이스케이프(\\n, \\u0000 등)를 그대로 넣을 수 있게 문자열로 붙인다.
    private MockHttpServletRequestBuilder write(String title, String content) {
        String body = "{\"title\":\"" + title + "\",\"content\":\"" + content + "\"}";
        return json(post("/api/v1/notices"), body).with(as(AUTHOR, Role.STAFF));
    }

    private RequestPostProcessor as(long id, Role role) {
        return loginUser(id, role);
    }

    private void saveUser(long id, String role) {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, name, password_hash, role) OVERRIDING SYSTEM VALUE"
                        + " VALUES (?, ?, ?, 'x', ?)",
                id,
                "user" + id + "@example.com",
                "회원" + id,
                role);
    }

    private long saveNotice(long authorId) {
        Timestamp at = Timestamp.from(BASE);
        return jdbcTemplate.queryForObject(
                "INSERT INTO post (board_type, title, content, author_id, created_at, updated_at)"
                        + " VALUES ('NOTICE', '원래 제목', '원래 본문', ?, ?, ?) RETURNING id",
                Long.class,
                authorId,
                at,
                at);
    }

    private String titleOf(long id) {
        return jdbcTemplate.queryForObject("SELECT title FROM post WHERE id = ?", String.class, id);
    }

    private int postCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM post", Integer.class);
    }
}
