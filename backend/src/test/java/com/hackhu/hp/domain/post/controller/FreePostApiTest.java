package com.hackhu.hp.domain.post.controller;

import static com.hackhu.hp.global.auth.LoginUserRequests.loginUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
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
 * 자유게시판 API (#53). 동아리원 전용이라 거부 케이스를 먼저 둔다 (code-style.md "테스트").
 *
 * <p>설계 5-1-4의 교차 접근 표(공지 API에 자유게시판 글 ID, 자유게시판 API에 공지 ID)를 실제 요청으로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class FreePostApiTest {

    private static final long AUTHOR = 9201L;
    private static final long OTHER_MEMBER = 9202L;
    private static final long ADMIN = 9203L;
    private static final Instant BASE = Instant.parse("2026-11-16T09:30:00Z");
    private static final String BODY = "{\"title\":\"스터디 후기\",\"content\":\"재밌었다\"}";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean AuthorNameReader authorNameReader;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM post");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (9201, 9202, 9203)");
        saveUser(AUTHOR, "MEMBER");
        saveUser(OTHER_MEMBER, "MEMBER");
        saveUser(ADMIN, "ADMIN");
        given(authorNameReader.findNames(anyCollection())).willReturn(Map.of(AUTHOR, "김동아"));
    }

    // 동아리원이 아닌 사람

    @Test
    void 로그인하지_않으면_목록_상세_작성_모두_401이다() throws Exception {
        long id = savePost("FREE", AUTHOR, BASE);

        mockMvc.perform(get("/api/v1/free-posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/free-posts/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/v1/free-posts"), BODY))
                .andExpect(status().isUnauthorized());
        assertThat(postCount()).isEqualTo(1);
    }

    @Test
    void 승인_대기_회원은_목록과_상세를_볼_수_없다() throws Exception {
        long id = savePost("FREE", AUTHOR, BASE);

        mockMvc.perform(get("/api/v1/free-posts").with(as(AUTHOR, Role.PENDING)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/v1/free-posts/{id}", id).with(as(AUTHOR, Role.PENDING)))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/v1/free-posts"), BODY).with(as(AUTHOR, Role.PENDING)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 동아리원이_아니면_없는_글_ID로_불러도_같은_응답이라_글이_있는지_알_수_없다() throws Exception {
        long existing = savePost("FREE", AUTHOR, BASE);

        mockMvc.perform(get("/api/v1/free-posts/{id}", existing))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/free-posts/{id}", 999_999))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/free-posts/{id}", existing).with(as(AUTHOR, Role.PENDING)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/free-posts/{id}", 999_999).with(as(AUTHOR, Role.PENDING)))
                .andExpect(status().isForbidden());
    }

    // 게시판 종류 구분 (5-1-4)

    @Test
    void 공지_API로_자유게시판_글을_열면_404다() throws Exception {
        long freeId = savePost("FREE", AUTHOR, BASE);

        // 공지 상세는 공개 API다. 여기로 동아리원 전용 글이 새면 방문자가 읽는다.
        mockMvc.perform(get("/api/v1/notices/{id}", freeId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/notices")).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void 자유게시판_API로_공지를_열거나_고치거나_지우면_404다() throws Exception {
        long noticeId = savePost("NOTICE", AUTHOR, BASE);

        mockMvc.perform(get("/api/v1/free-posts/{id}", noticeId).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        mockMvc.perform(
                        json(put("/api/v1/free-posts/{id}", noticeId), BODY)
                                .with(as(ADMIN, Role.ADMIN)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/free-posts/{id}", noticeId).with(as(ADMIN, Role.ADMIN)))
                .andExpect(status().isNotFound());
        assertThat(titleOf(noticeId)).isEqualTo("원래 제목");
        mockMvc.perform(get("/api/v1/free-posts").with(as(AUTHOR, Role.MEMBER)))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // 동아리원

    @Test
    void 동아리원은_목록을_최신순으로_본다() throws Exception {
        savePost("FREE", AUTHOR, BASE);
        long newer = savePost("FREE", AUTHOR, BASE.plusSeconds(60));

        mockMvc.perform(get("/api/v1/free-posts").with(as(OTHER_MEMBER, Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(newer))
                .andExpect(jsonPath("$.content[0].author.name").value("김동아"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist())
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void 목록의_size는_20을_넘지_않고_sort는_createdAt_desc만_받는다() throws Exception {
        for (int i = 0; i < 25; i++) {
            savePost("FREE", AUTHOR, BASE.plusSeconds(i));
        }

        mockMvc.perform(
                        get("/api/v1/free-posts")
                                .param("size", "100")
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(jsonPath("$.content", hasSize(20)));
        mockMvc.perform(
                        get("/api/v1/free-posts")
                                .param("sort", "authorId,desc")
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 동아리원은_상세를_본다() throws Exception {
        long id = savePost("FREE", AUTHOR, BASE);

        mockMvc.perform(get("/api/v1/free-posts/{id}", id).with(as(OTHER_MEMBER, Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("원래 제목"))
                .andExpect(jsonPath("$.content").value("원래 본문"))
                .andExpect(jsonPath("$.author.id").value(AUTHOR));
    }

    @Test
    void 동아리원이_글을_쓰면_201이고_작성자와_게시판_종류는_요청으로_바꿀_수_없다() throws Exception {
        String body =
                "{\"title\":\"스터디 후기\",\"content\":\"재밌었다\",\"authorId\":9203,\"boardType\":\"NOTICE\"}";

        mockMvc.perform(json(post("/api/v1/free-posts"), body).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/free-posts/\\d+")))
                .andExpect(jsonPath("$.author.id").value(AUTHOR));
        assertThat(jdbcTemplate.queryForObject("SELECT board_type FROM post", String.class))
                .isEqualTo("FREE");
        assertThat(jdbcTemplate.queryForObject("SELECT author_id FROM post", Long.class))
                .isEqualTo(AUTHOR);
    }

    @Test
    void 제목이_100자를_넘으면_400이다() throws Exception {
        String body = "{\"title\":\"" + "가".repeat(101) + "\",\"content\":\"본문\"}";

        mockMvc.perform(json(post("/api/v1/free-posts"), body).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    // 수정, 삭제

    @Test
    void 남의_글은_고치거나_지울_수_없다() throws Exception {
        long id = savePost("FREE", AUTHOR, BASE);

        mockMvc.perform(
                        json(put("/api/v1/free-posts/{id}", id), BODY)
                                .with(as(OTHER_MEMBER, Role.MEMBER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_FORBIDDEN"));
        mockMvc.perform(delete("/api/v1/free-posts/{id}", id).with(as(OTHER_MEMBER, Role.MEMBER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_FORBIDDEN"));
        assertThat(titleOf(id)).isEqualTo("원래 제목");
    }

    @Test
    void 없는_글을_고치면_404다() throws Exception {
        mockMvc.perform(
                        json(put("/api/v1/free-posts/{id}", 999_999), BODY)
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void 작성자가_글을_고치면_작성자와_작성_시각은_그대로고_수정_시각은_늦어진다() throws Exception {
        long id = savePost("FREE", AUTHOR, BASE);

        mockMvc.perform(
                        json(put("/api/v1/free-posts/{id}", id), BODY)
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("스터디 후기"))
                .andExpect(jsonPath("$.author.id").value(AUTHOR))
                .andExpect(jsonPath("$.createdAt").value("2026-11-16T09:30:00Z"))
                // 응답 스키마가 "수정한 적이 없으면 createdAt과 같다"이므로 수정 뒤에는 달라야 한다.
                .andExpect(jsonPath("$.updatedAt").value(not("2026-11-16T09:30:00Z")));

        Timestamp updatedAt =
                jdbcTemplate.queryForObject(
                        "SELECT updated_at FROM post WHERE id = ?", Timestamp.class, id);
        assertThat(updatedAt.toInstant()).isAfter(BASE);
    }

    @Test
    void 작성자가_글을_지우면_204이고_다시_보면_404다() throws Exception {
        long id = savePost("FREE", AUTHOR, BASE);

        mockMvc.perform(delete("/api/v1/free-posts/{id}", id).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/free-posts/{id}", id).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void 관리자는_남의_글을_고치고_지우며_기록을_남긴다(CapturedOutput output) throws Exception {
        long id = savePost("FREE", AUTHOR, BASE);

        mockMvc.perform(json(put("/api/v1/free-posts/{id}", id), BODY).with(as(ADMIN, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.id").value(AUTHOR));
        mockMvc.perform(delete("/api/v1/free-posts/{id}", id).with(as(ADMIN, Role.ADMIN)))
                .andExpect(status().isNoContent());

        assertThat(output.getOut())
                .contains("관리자가 남의 글을 수정함 adminId=" + ADMIN + " postId=" + id + " boardType=FREE")
                .contains("관리자가 남의 글을 삭제함 adminId=" + ADMIN + " postId=" + id)
                .doesNotContain("스터디 후기");
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
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

    private long savePost(String boardType, long authorId, Instant createdAt) {
        Timestamp at = Timestamp.from(createdAt);
        return jdbcTemplate.queryForObject(
                "INSERT INTO post (board_type, title, content, author_id, created_at, updated_at)"
                        + " VALUES (?, '원래 제목', '원래 본문', ?, ?, ?) RETURNING id",
                Long.class,
                boardType,
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
