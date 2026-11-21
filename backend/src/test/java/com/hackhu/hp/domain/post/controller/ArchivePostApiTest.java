package com.hackhu.hp.domain.post.controller;

import static com.hackhu.hp.global.auth.LoginUserRequests.loginUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.TestcontainersConfiguration;
import com.hackhu.hp.domain.post.service.AuthorNameReader;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.storage.PresignedUrlService;
import com.hackhu.hp.global.storage.S3ObjectStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import software.amazon.awssdk.core.exception.SdkClientException;

/**
 * 자료게시판 API (#55). S3는 흉내 내고 DB는 실제 PostgreSQL로 확인한다.
 *
 * <p>업로드 발급 기록은 발급 API를 거치지 않고 직접 넣는다. 요청 횟수 제한이 테스트 사이에 남아 서로 영향을 주지 않게 하려는 것이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ArchivePostApiTest {

    private static final long AUTHOR = 9301L;
    private static final long OTHER_MEMBER = 9302L;
    private static final long ADMIN = 9303L;
    private static final String PDF = "application/pdf";
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final byte[] PDF_HEAD = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean AuthorNameReader authorNameReader;
    @MockitoBean S3ObjectStore objectStore;
    @MockitoBean PresignedUrlService presignedUrlService;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM post");
        jdbcTemplate.update("DELETE FROM upload_record");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (9301, 9302, 9303)");
        saveUser(AUTHOR, "MEMBER");
        saveUser(OTHER_MEMBER, "MEMBER");
        saveUser(ADMIN, "ADMIN");
        given(authorNameReader.findNames(anyCollection())).willReturn(Map.of(AUTHOR, "김동아"));
        given(objectStore.readHead(anyString(), anyInt())).willReturn(PDF_HEAD);
        given(presignedUrlService.presignPut(anyString(), anyString(), anyLong(), any()))
                .willReturn(URI.create("https://s3.example/put").toURL());
        given(presignedUrlService.presignGet(anyString(), anyString(), anyString(), any()))
                .willReturn(URI.create("https://s3.example/get").toURL());
    }

    // 동아리원이 아닌 사람

    @Test
    void 로그인하지_않으면_401_승인_대기면_403이다() throws Exception {
        long id = savePost("ARCHIVE", AUTHOR);

        mockMvc.perform(get("/api/v1/archive-posts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/archive-posts/{id}/attachments/1/download-url", id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/archive-posts/{id}", id).with(as(AUTHOR, Role.PENDING)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(
                        json(
                                        post("/api/v1/archive-posts/upload-urls"),
                                        uploadRequest("a.pdf", PDF, 10))
                                .with(as(AUTHOR, Role.PENDING)))
                .andExpect(status().isForbidden());
    }

    // 게시판 종류 구분 (5-1-4)

    @Test
    void 다른_게시판_API로_자료_글을_열면_404다() throws Exception {
        long archiveId = savePost("ARCHIVE", AUTHOR);
        long freeId = savePost("FREE", AUTHOR);

        mockMvc.perform(get("/api/v1/notices/{id}", archiveId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/free-posts/{id}", archiveId).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/archive-posts/{id}", freeId).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    // 업로드 주소 발급 (4-2-10)

    @Test
    void 업로드_주소를_발급하고_경로를_지운_파일_이름을_기록한다() throws Exception {
        mockMvc.perform(
                        json(
                                        post("/api/v1/archive-posts/upload-urls"),
                                        uploadRequest("C:\\\\자료\\\\3주차.PDF", PDF, 1024))
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploads", hasSize(1)))
                .andExpect(jsonPath("$.uploads[0].uploadId").isNotEmpty())
                .andExpect(jsonPath("$.uploads[0].uploadUrl").value("https://s3.example/put"))
                .andExpect(jsonPath("$.uploads[0].objectKey").doesNotExist());

        Map<String, Object> record = jdbcTemplate.queryForMap("SELECT * FROM upload_record");
        assertThat(record.get("purpose")).isEqualTo("ATTACHMENT");
        assertThat(record.get("original_file_name")).isEqualTo("3주차.PDF");
        assertThat(record.get("content_type")).isEqualTo(PDF);
        verify(presignedUrlService)
                .presignPut(anyString(), eq(PDF), eq(1024L), eq(Duration.ofMinutes(10)));
    }

    @Test
    void docx_MIME_타입이_길어도_발급_기록에_들어간다() throws Exception {
        mockMvc.perform(
                        json(
                                        post("/api/v1/archive-posts/upload-urls"),
                                        uploadRequest("a.docx", DOCX, 10))
                                .with(as(OTHER_MEMBER, Role.MEMBER)))
                .andExpect(status().isOk());

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT content_type FROM upload_record", String.class))
                .isEqualTo(DOCX);
    }

    @Test
    void 허용하지_않는_형식이나_짝이_맞지_않는_형식은_발급_전에_거부한다() throws Exception {
        for (String body :
                List.of(
                        uploadRequest("보고서.pdf.exe", PDF, 10),
                        uploadRequest("a.pdf", DOCX, 10),
                        uploadRequest("a.html", "text/html", 10),
                        uploadRequest("확장자없음", PDF, 10))) {
            mockMvc.perform(
                            json(post("/api/v1/archive-posts/upload-urls"), body)
                                    .with(as(AUTHOR, Role.MEMBER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("POST_UNSUPPORTED_FORMAT"));
        }
        assertThat(uploadCount()).isZero();
    }

    @Test
    void 이십MB를_넘으면_거부한다() throws Exception {
        mockMvc.perform(
                        json(
                                        post("/api/v1/archive-posts/upload-urls"),
                                        uploadRequest("a.pdf", PDF, 20_971_521))
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POST_FILE_TOO_LARGE"));
    }

    @Test
    void 파일_이름의_줄바꿈과_양방향_문자를_거부한다() throws Exception {
        for (String name : List.of("a\\r\\nX-Evil: 1.pdf", "abc\\u202Efdp.exe.pdf")) {
            mockMvc.perform(
                            json(
                                            post("/api/v1/archive-posts/upload-urls"),
                                            uploadRequest(name, PDF, 10))
                                    .with(as(AUTHOR, Role.MEMBER)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
    }

    @Test
    void 한_번에_6개는_발급하지_않는다() throws Exception {
        String file = "{\"fileName\":\"a.pdf\",\"contentType\":\"" + PDF + "\",\"size\":10}";
        String body =
                "{\"files\":[" + String.join(",", java.util.Collections.nCopies(6, file)) + "]}";

        mockMvc.perform(
                        json(post("/api/v1/archive-posts/upload-urls"), body)
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isBadRequest());
    }

    // 작성 (4-2-9)

    @Test
    void 첨부를_붙여_쓰면_표시_순서대로_보이고_업로드는_다시_쓸_수_없다() throws Exception {
        UUID first = saveUpload(AUTHOR, "1주차.pdf", PDF);
        UUID second = saveUpload(AUTHOR, "실습.docx", DOCX);
        given(objectStore.readHead(eq("attachment/" + second), anyInt()))
                .willReturn(new byte[] {'P', 'K', 3, 4, 20});

        String body = writeBody(List.of(first, second));
        String location =
                mockMvc.perform(
                                json(post("/api/v1/archive-posts"), body)
                                        .with(as(AUTHOR, Role.MEMBER)))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.attachments", hasSize(2)))
                        .andExpect(jsonPath("$.attachments[0].fileName").value("1주차.pdf"))
                        .andExpect(jsonPath("$.attachments[1].fileName").value("실습.docx"))
                        .andExpect(jsonPath("$.attachments[1].contentType").value(DOCX))
                        .andExpect(jsonPath("$.attachments[0].objectKey").doesNotExist())
                        .andReturn()
                        .getResponse()
                        .getHeader("Location");
        assertThat(location).startsWith("/api/v1/archive-posts/");

        mockMvc.perform(get("/api/v1/archive-posts").with(as(OTHER_MEMBER, Role.MEMBER)))
                .andExpect(jsonPath("$.content[0].attachmentCount").value(2));

        // 같은 uploadId로 다른 글에 붙이려 하면 거부된다.
        mockMvc.perform(
                        json(post("/api/v1/archive-posts"), writeBody(List.of(first)))
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POST_INVALID_UPLOAD"))
                .andExpect(jsonPath("$.errors[0].field").value("uploadIds[0]"));
    }

    @Test
    void 첨부_없이도_쓸_수_있고_목록의_첨부_수는_0이다() throws Exception {
        mockMvc.perform(
                        json(post("/api/v1/archive-posts"), writeBody(List.of()))
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachments", hasSize(0)));

        mockMvc.perform(get("/api/v1/archive-posts").with(as(AUTHOR, Role.MEMBER)))
                .andExpect(jsonPath("$.content[0].attachmentCount").value(0));
    }

    @Test
    void 실제_형식이_신고와_다르면_글을_만들지_않고_객체를_지운다(CapturedOutput output) throws Exception {
        UUID upload = saveUpload(AUTHOR, "a.pdf", PDF);
        given(objectStore.readHead(anyString(), anyInt()))
                .willReturn(new byte[] {'M', 'Z', 0, 0, 0});

        mockMvc.perform(
                        json(post("/api/v1/archive-posts"), writeBody(List.of(upload)))
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POST_INVALID_UPLOAD"));

        assertThat(postCount()).isZero();
        verify(objectStore).deleteAll(List.of("attachment/" + upload));
        assertThat(output.getOut()).contains("업로드 형식 확인 실패 userId=" + AUTHOR);
    }

    @Test
    void 남이_발급받은_업로드는_붙일_수_없다() throws Exception {
        UUID stolen = saveUpload(OTHER_MEMBER, "a.pdf", PDF);

        mockMvc.perform(
                        json(post("/api/v1/archive-posts"), writeBody(List.of(stolen)))
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POST_INVALID_UPLOAD"));
        assertThat(postCount()).isZero();
    }

    @Test
    void 같은_uploadId를_두_번_보내면_400이다() throws Exception {
        UUID upload = saveUpload(AUTHOR, "a.pdf", PDF);

        mockMvc.perform(
                        json(post("/api/v1/archive-posts"), writeBody(List.of(upload, upload)))
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // 수정 (4-2-9, DECISIONS.md B10)

    @Test
    void 수정에서_첨부를_남기고_빼고_더하며_뺀_객체는_커밋_뒤에_지운다() throws Exception {
        long postId = savePost("ARCHIVE", AUTHOR);
        long keepA = saveAttachment(postId, "a.pdf", 0);
        long drop = saveAttachment(postId, "b.pdf", 1);
        long keepC = saveAttachment(postId, "c.pdf", 2);
        UUID added = saveUpload(AUTHOR, "new.pdf", PDF);

        String body =
                "{\"title\":\"수정\",\"content\":\"본문\",\"attachmentIds\":["
                        + keepC
                        + ","
                        + keepA
                        + "],\"uploadIds\":[\""
                        + added
                        + "\"]}";
        mockMvc.perform(
                        json(put("/api/v1/archive-posts/{id}", postId), body)
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments", hasSize(3)))
                .andExpect(jsonPath("$.attachments[0].id").value(keepC))
                .andExpect(jsonPath("$.attachments[1].id").value(keepA))
                .andExpect(jsonPath("$.attachments[2].fileName").value("new.pdf"));

        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT display_order FROM attachment WHERE post_id = ? ORDER BY id",
                                Integer.class,
                                postId))
                .containsExactly(1, 0, 2);
        // 확정이 영속성 컨텍스트를 비운 뒤에도 제목 수정이 DB에 반영돼야 한다.
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT title FROM post WHERE id = ?", String.class, postId))
                .isEqualTo("수정");
        verify(objectStore).deleteAll(List.of("attachment/obj-" + drop));
    }

    @Test
    void 새_업로드_없이_첨부_순서만_바꿔도_반영된다() throws Exception {
        long postId = savePost("ARCHIVE", AUTHOR);
        long a = saveAttachment(postId, "a.pdf", 0);
        long b = saveAttachment(postId, "b.pdf", 1);

        String body =
                "{\"title\":\"순서\",\"content\":\"본문\",\"attachmentIds\":[" + b + "," + a + "]}";
        mockMvc.perform(
                        json(put("/api/v1/archive-posts/{id}", postId), body)
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments[0].id").value(b));

        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT display_order FROM attachment WHERE post_id = ? ORDER BY id",
                                Integer.class,
                                postId))
                .containsExactly(1, 0);
        verify(objectStore, never()).deleteAll(any());
    }

    @Test
    void 다른_글의_첨부_ID를_남기려_하면_400이다() throws Exception {
        long mine = savePost("ARCHIVE", AUTHOR);
        long other = savePost("ARCHIVE", OTHER_MEMBER);
        long othersAttachment = saveAttachment(other, "x.pdf", 0);

        String body =
                "{\"title\":\"t\",\"content\":\"c\",\"attachmentIds\":[" + othersAttachment + "]}";
        mockMvc.perform(
                        json(put("/api/v1/archive-posts/{id}", mine), body)
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT post_id FROM attachment WHERE id = ?",
                                Long.class,
                                othersAttachment))
                .isEqualTo(other);
    }

    @Test
    void 남길_첨부와_새_업로드가_합쳐_5개를_넘으면_거부하고_업로드는_남긴다() throws Exception {
        long postId = savePost("ARCHIVE", AUTHOR);
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            ids.append(i == 0 ? "" : ",").append(saveAttachment(postId, i + ".pdf", i));
        }
        UUID extra = saveUpload(AUTHOR, "6.pdf", PDF);

        String body =
                "{\"title\":\"t\",\"content\":\"c\",\"attachmentIds\":["
                        + ids
                        + "],\"uploadIds\":[\""
                        + extra
                        + "\"]}";
        mockMvc.perform(
                        json(put("/api/v1/archive-posts/{id}", postId), body)
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("POST_TOO_MANY_ATTACHMENTS"));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM upload_record WHERE id = ?",
                                String.class,
                                extra))
                .isEqualTo("ISSUED");
    }

    @Test
    void 작성자도_관리자도_아니면_수정과_삭제는_403이다() throws Exception {
        long postId = savePost("ARCHIVE", AUTHOR);
        String body = "{\"title\":\"t\",\"content\":\"c\",\"attachmentIds\":[]}";

        mockMvc.perform(
                        json(put("/api/v1/archive-posts/{id}", postId), body)
                                .with(as(OTHER_MEMBER, Role.MEMBER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POST_FORBIDDEN"));
        mockMvc.perform(
                        delete("/api/v1/archive-posts/{id}", postId)
                                .with(as(OTHER_MEMBER, Role.MEMBER)))
                .andExpect(status().isForbidden());
    }

    // 삭제

    @Test
    void 삭제하면_첨부_행과_S3_객체를_함께_지운다() throws Exception {
        long postId = savePost("ARCHIVE", AUTHOR);
        long a = saveAttachment(postId, "a.pdf", 0);
        long b = saveAttachment(postId, "b.pdf", 1);

        mockMvc.perform(delete("/api/v1/archive-posts/{id}", postId).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM attachment", Integer.class))
                .isZero();
        verify(objectStore).deleteAll(List.of("attachment/obj-" + a));
        verify(objectStore).deleteAll(List.of("attachment/obj-" + b));
    }

    @Test
    void S3_삭제가_실패해도_삭제는_성공하고_객체_키를_경고로_남긴다(CapturedOutput output) throws Exception {
        long postId = savePost("ARCHIVE", AUTHOR);
        long a = saveAttachment(postId, "a.pdf", 0);
        willThrow(SdkClientException.create("down")).given(objectStore).deleteAll(any());

        mockMvc.perform(delete("/api/v1/archive-posts/{id}", postId).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isNoContent());

        assertThat(postCount()).isZero();
        assertThat(output.getOut())
                .contains("첨부 S3 객체 삭제 실패 objectKey=attachment/obj-" + a + " postId=" + postId);
    }

    // 다운로드 주소 발급 (4-2-11)

    @Test
    void 다운로드_주소를_원래_파일_이름과_저장한_형식으로_발급한다(CapturedOutput output) throws Exception {
        long postId = savePost("ARCHIVE", AUTHOR);
        long attachmentId = saveAttachment(postId, "3주차 자료.pdf", 0);

        mockMvc.perform(
                        post(
                                        "/api/v1/archive-posts/{p}/attachments/{a}/download-url",
                                        postId,
                                        attachmentId)
                                .with(as(OTHER_MEMBER, Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value("https://s3.example/get"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        verify(presignedUrlService)
                .presignGet(
                        "attachment/obj-" + attachmentId,
                        PDF,
                        "3주차 자료.pdf",
                        Duration.ofMinutes(10));
        assertThat(output.getOut())
                .contains(
                        "첨부 다운로드 주소 발급 userId="
                                + OTHER_MEMBER
                                + " postId="
                                + postId
                                + " attachmentId="
                                + attachmentId);
    }

    @Test
    void 다른_글의_첨부_ID로는_받을_수_없다() throws Exception {
        long postId = savePost("ARCHIVE", AUTHOR);
        long other = savePost("ARCHIVE", AUTHOR);
        long othersAttachment = saveAttachment(other, "x.pdf", 0);

        mockMvc.perform(
                        post(
                                        "/api/v1/archive-posts/{p}/attachments/{a}/download-url",
                                        postId,
                                        othersAttachment)
                                .with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_ATTACHMENT_NOT_FOUND"));
        verify(presignedUrlService, never())
                .presignGet(anyString(), anyString(), anyString(), any());
    }

    @Test
    void 상세에는_다운로드_주소를_넣지_않는다() throws Exception {
        long postId = savePost("ARCHIVE", AUTHOR);
        saveAttachment(postId, "a.pdf", 0);

        mockMvc.perform(get("/api/v1/archive-posts/{id}", postId).with(as(AUTHOR, Role.MEMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachments[0].fileName").value("a.pdf"))
                .andExpect(jsonPath("$.attachments[0].size").value(100))
                .andExpect(jsonPath("$.attachments[0].downloadUrl").doesNotExist());
        verify(presignedUrlService, never())
                .presignGet(anyString(), anyString(), anyString(), any());
    }

    // 도우미

    private static String uploadRequest(String fileName, String contentType, long size) {
        return "{\"files\":[{\"fileName\":\""
                + fileName
                + "\",\"contentType\":\""
                + contentType
                + "\",\"size\":"
                + size
                + "}]}";
    }

    private static String writeBody(List<UUID> uploadIds) {
        String ids = String.join(",", uploadIds.stream().map(id -> "\"" + id + "\"").toList());
        return "{\"title\":\"3주차 자료\",\"content\":\"자료입니다\",\"uploadIds\":[" + ids + "]}";
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

    private long savePost(String boardType, long authorId) {
        Timestamp at = Timestamp.from(Instant.parse("2026-11-20T11:00:00Z"));
        return jdbcTemplate.queryForObject(
                "INSERT INTO post (board_type, title, content, author_id, created_at, updated_at)"
                        + " VALUES (?, '원래 제목', '원래 본문', ?, ?, ?) RETURNING id",
                Long.class,
                boardType,
                authorId,
                at,
                at);
    }

    /** 객체 키는 attachment/obj-{id}가 되도록 넣은 뒤 고친다. 검증에서 키를 예측하기 쉽게 하려는 것이다. */
    private long saveAttachment(long postId, String fileName, int order) {
        long id =
                jdbcTemplate.queryForObject(
                        "INSERT INTO attachment (post_id, object_key, file_name, content_type,"
                                + " size_bytes, display_order) VALUES (?, ?, ?, ?, 100, ?) RETURNING id",
                        Long.class,
                        postId,
                        "tmp/" + UUID.randomUUID(),
                        fileName,
                        PDF,
                        order);
        jdbcTemplate.update(
                "UPDATE attachment SET object_key = ? WHERE id = ?", "attachment/obj-" + id, id);
        return id;
    }

    private UUID saveUpload(long userId, String fileName, String contentType) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO upload_record (id, user_id, purpose, object_key, content_type,"
                        + " size_bytes, status, expires_at, created_at, original_file_name)"
                        + " VALUES (?, ?, 'ATTACHMENT', ?, ?, 2048, 'ISSUED', ?, ?, ?)",
                id,
                userId,
                "attachment/" + id,
                contentType,
                Timestamp.from(now.plus(Duration.ofMinutes(10))),
                Timestamp.from(now),
                fileName);
        return id;
    }

    private int postCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM post", Integer.class);
    }

    private int uploadCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM upload_record", Integer.class);
    }
}
