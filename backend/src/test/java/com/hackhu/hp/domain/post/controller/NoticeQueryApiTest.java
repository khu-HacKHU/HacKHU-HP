package com.hackhu.hp.domain.post.controller;

import static com.hackhu.hp.global.auth.LoginUserRequests.loginUser;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.TestcontainersConfiguration;
import com.hackhu.hp.domain.post.service.AuthorNameReader;
import com.hackhu.hp.global.auth.Role;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 공지 목록, 상세 조회 API (#29). 실제 PostgreSQL(Testcontainers)과 Flyway 마이그레이션으로 확인한다.
 *
 * <p>요청에 인증 정보를 하나도 붙이지 않는다. 조회는 공개 API라서 로그인 없이 200이어야 한다 (USER-STORIES.md 2절).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NoticeQueryApiTest {

    private static final String ISO_UTC = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";
    private static final Instant BASE = Instant.parse("2026-11-07T09:30:00Z");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    // 작성자 이름은 인증권한팀 도메인이라 가짜로 채운다. 9001번은 활동 중, 9002번은 탈퇴해서 이름이 없다.
    @MockitoBean AuthorNameReader authorNameReader;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM post");
        // post.author_id는 users.id를 참조한다. 다른 테스트가 만드는 회원과 ID가 겹치지 않게 큰 번호를 직접 넣는다.
        jdbcTemplate.update("DELETE FROM users WHERE id IN (9001, 9002)");
        jdbcTemplate.update(
                "INSERT INTO users (id, email, name, password_hash, role) OVERRIDING SYSTEM VALUE"
                        + " VALUES (9001, 'staff9001@example.com', '홍길동', 'x', 'STAFF')");
        jdbcTemplate.update(
                "INSERT INTO users (id, role, withdrawn_at) OVERRIDING SYSTEM VALUE"
                        + " VALUES (9002, 'STAFF', now())");
        Map<Long, String> names = new HashMap<>();
        names.put(9001L, "홍길동");
        names.put(9002L, null);
        given(authorNameReader.findNames(anyCollection())).willReturn(names);
    }

    @Test
    void 로그인하지_않아도_목록을_본다() throws Exception {
        long id = saveNotice("11월 정기 세미나 안내", "본문", 9001L, BASE);

        mockMvc.perform(get("/api/v1/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(id))
                .andExpect(jsonPath("$.content[0].title").value("11월 정기 세미나 안내"))
                .andExpect(jsonPath("$.content[0].author.id").value(9001))
                .andExpect(jsonPath("$.content[0].author.name").value("홍길동"))
                .andExpect(jsonPath("$.content[0].createdAt").value(matchesPattern(ISO_UTC)))
                .andExpect(jsonPath("$.content[0].updatedAt").value(matchesPattern(ISO_UTC)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void 목록에는_본문을_넣지_않는다() throws Exception {
        saveNotice("제목", "본문", 9001L, BASE);

        mockMvc.perform(get("/api/v1/notices"))
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
    }

    @Test
    void 목록은_최신_공지가_먼저_온다() throws Exception {
        saveNotice("첫째", "본문", 9001L, BASE);
        saveNotice("셋째", "본문", 9001L, BASE.plusSeconds(120));
        saveNotice("둘째", "본문", 9001L, BASE.plusSeconds(60));

        mockMvc.perform(get("/api/v1/notices"))
                .andExpect(jsonPath("$.content[0].title").value("셋째"))
                .andExpect(jsonPath("$.content[1].title").value("둘째"))
                .andExpect(jsonPath("$.content[2].title").value("첫째"));
    }

    @Test
    void 작성_시각이_같으면_나중에_쓴_공지가_먼저_와서_페이지가_겹치지_않는다() throws Exception {
        saveNotice("먼저", "본문", 9001L, BASE);
        saveNotice("나중", "본문", 9001L, BASE);

        mockMvc.perform(get("/api/v1/notices").param("page", "0").param("size", "1"))
                .andExpect(jsonPath("$.content[0].title").value("나중"))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/v1/notices").param("page", "1").param("size", "1"))
                .andExpect(jsonPath("$.content[0].title").value("먼저"));
    }

    @Test
    void 메인_화면은_size_5로_최근_공지_5개를_받는다() throws Exception {
        saveNotices(7);

        mockMvc.perform(get("/api/v1/notices").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(7));
    }

    @Test
    void size가_20을_넘으면_20으로_맞춘다() throws Exception {
        saveNotices(25);

        mockMvc.perform(get("/api/v1/notices").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void size가_1보다_작으면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/notices").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void page가_음수면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/notices").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void page가_숫자가_아니면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/notices").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void sort는_createdAt_desc만_받는다() throws Exception {
        saveNotice("제목", "본문", 9001L, BASE);

        mockMvc.perform(get("/api/v1/notices").param("sort", "createdAt,desc"))
                .andExpect(status().isOk());
    }

    @Test
    void 다른_sort는_쿼리를_실행하기_전에_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/notices").param("sort", "authorId,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 마지막_페이지를_넘으면_빈_목록이다() throws Exception {
        saveNotice("제목", "본문", 9001L, BASE);

        mockMvc.perform(get("/api/v1/notices").param("page", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void 공지가_없으면_빈_목록이다() throws Exception {
        mockMvc.perform(get("/api/v1/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 로그인하지_않아도_상세를_본다() throws Exception {
        long id = saveNotice("세미나 안내", "일시: 11월 14일 19시\n장소: 동아리방", 9001L, BASE);

        mockMvc.perform(get("/api/v1/notices/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("세미나 안내"))
                .andExpect(jsonPath("$.content").value("일시: 11월 14일 19시\n장소: 동아리방"))
                .andExpect(jsonPath("$.author.id").value(9001))
                .andExpect(jsonPath("$.author.name").value("홍길동"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-07T09:30:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-07T09:30:00Z"));
    }

    @Test
    void 본문의_HTML은_바꾸지_않고_글자_그대로_준다() throws Exception {
        long id = saveNotice("제목", "<script>alert(1)</script>", 9001L, BASE);

        mockMvc.perform(get("/api/v1/notices/{id}", id))
                .andExpect(jsonPath("$.content").value("<script>alert(1)</script>"));
    }

    @Test
    void 탈퇴한_작성자는_이름이_null이다() throws Exception {
        long id = saveNotice("제목", "본문", 9002L, BASE);

        mockMvc.perform(get("/api/v1/notices/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author.id").value(9002))
                .andExpect(jsonPath("$.author.name").doesNotExist());
    }

    @Test
    void 응답에_작성자_이메일과_역할을_넣지_않는다() throws Exception {
        long id = saveNotice("제목", "본문", 9001L, BASE);

        mockMvc.perform(get("/api/v1/notices/{id}", id))
                .andExpect(jsonPath("$.author.email").doesNotExist())
                .andExpect(jsonPath("$.author.role").doesNotExist())
                .andExpect(jsonPath("$.boardType").doesNotExist());
    }

    @Test
    void 없는_공지는_404다() throws Exception {
        mockMvc.perform(get("/api/v1/notices/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void 공지_ID가_숫자가_아니면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/notices/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 공지_ID가_1보다_작으면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/notices/{id}", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 승인_대기_회원도_공지를_본다() throws Exception {
        saveNotice("제목", "본문", 9001L, BASE);

        mockMvc.perform(get("/api/v1/notices").with(loginUser(42L, Role.PENDING)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void 공개는_GET_목록과_상세뿐이다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/notices")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/notices/1/attachments")).andExpect(status().isUnauthorized());
    }

    @Test
    void 작성자가_없는_회원이면_DB가_막는다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> saveNotice("제목", "본문", 123456789L, BASE))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    // 작성 시각을 정해서 넣어야 정렬을 확인할 수 있어서 JPA 대신 SQL로 넣는다.
    private long saveNotice(String title, String content, long authorId, Instant createdAt) {
        Timestamp at = Timestamp.from(createdAt);
        Long id =
                jdbcTemplate.queryForObject(
                        "INSERT INTO post (board_type, title, content, author_id, created_at,"
                                + " updated_at) VALUES ('NOTICE', ?, ?, ?, ?, ?) RETURNING id",
                        Long.class,
                        title,
                        content,
                        authorId,
                        at,
                        at);
        return id;
    }

    private void saveNotices(int count) {
        for (int i = 0; i < count; i++) {
            saveNotice("공지 " + i, "본문", 9001L, BASE.plusSeconds(i));
        }
    }
}
