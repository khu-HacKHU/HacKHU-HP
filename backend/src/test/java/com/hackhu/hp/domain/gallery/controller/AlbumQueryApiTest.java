package com.hackhu.hp.domain.gallery.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.TestcontainersConfiguration;
import com.hackhu.hp.domain.gallery.entity.Album;
import com.hackhu.hp.domain.gallery.entity.Photo;
import com.hackhu.hp.domain.gallery.repository.AlbumRepository;
import com.hackhu.hp.domain.gallery.repository.PhotoRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 앨범 목록·상세 조회 API (#36). 실제 PostgreSQL(Testcontainers)과 Flyway 마이그레이션으로 확인한다.
 *
 * <p>요청에 인증 정보를 하나도 붙이지 않는다. 조회는 공개 API라서 로그인 없이 200이어야 한다 (USER-STORIES 1절).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AlbumQueryApiTest {

    private static final String ISO_UTC = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";

    @Autowired MockMvc mockMvc;
    @Autowired AlbumRepository albumRepository;
    @Autowired PhotoRepository photoRepository;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
    }

    @Test
    void 로그인하지_않아도_목록을_본다() throws Exception {
        Album album = saveAlbum("10월 정기 세미나", LocalDate.of(2026, 10, 18), 2);

        mockMvc.perform(get("/api/v1/albums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(album.getId()))
                .andExpect(jsonPath("$.content[0].title").value("10월 정기 세미나"))
                .andExpect(jsonPath("$.content[0].eventDate").value("2026-10-18"))
                .andExpect(jsonPath("$.content[0].createdAt").value(matchesPattern(ISO_UTC)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void 목록의_대표_사진은_첫_번째_사진의_서명_URL이다() throws Exception {
        Album album = saveAlbum("MT", LocalDate.of(2026, 9, 1), 3);

        mockMvc.perform(get("/api/v1/albums"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.content[0].coverPhotoUrl")
                                .value(
                                        allOf(
                                                startsWith(
                                                        "https://cdn.local.invalid/"
                                                                + objectKey(album, 0)),
                                                containsString("Expires="),
                                                containsString("Signature="),
                                                containsString("Key-Pair-Id="))));
    }

    @Test
    void 목록은_활동_날짜가_최근인_앨범이_먼저_온다() throws Exception {
        saveAlbum("9월", LocalDate.of(2026, 9, 10), 1);
        saveAlbum("11월", LocalDate.of(2026, 11, 1), 1);
        saveAlbum("10월", LocalDate.of(2026, 10, 5), 1);

        mockMvc.perform(get("/api/v1/albums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("11월"))
                .andExpect(jsonPath("$.content[1].title").value("10월"))
                .andExpect(jsonPath("$.content[2].title").value("9월"));
    }

    @Test
    void 활동_날짜가_같으면_나중에_올린_앨범이_먼저_와서_페이지가_겹치지_않는다() throws Exception {
        LocalDate sameDay = LocalDate.of(2026, 10, 1);
        saveAlbum("먼저", sameDay, 1);
        saveAlbum("나중", sameDay, 1);

        mockMvc.perform(get("/api/v1/albums").param("page", "0").param("size", "1"))
                .andExpect(jsonPath("$.content[0].title").value("나중"))
                .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/api/v1/albums").param("page", "1").param("size", "1"))
                .andExpect(jsonPath("$.content[0].title").value("먼저"));
    }

    @Test
    void 앨범이_없으면_빈_목록이다() throws Exception {
        mockMvc.perform(get("/api/v1/albums"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void size가_100을_넘으면_100으로_맞춘다() throws Exception {
        mockMvc.perform(get("/api/v1/albums").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void 허용한_정렬_값은_받는다() throws Exception {
        mockMvc.perform(get("/api/v1/albums").param("sort", "eventDate,desc"))
                .andExpect(status().isOk());
    }

    @Test
    void 허용하지_않은_정렬_필드는_400이다() throws Exception {
        // 없는 필드를 그대로 쿼리에 넘기면 500이 난다. 화이트리스트로 막는지 확인한다.
        mockMvc.perform(get("/api/v1/albums").param("sort", "uploaderId,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc.perform(get("/api/v1/albums").param("sort", "noSuchField"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 허용한_필드라도_오름차순은_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/albums").param("sort", "eventDate,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 응답에_작성자와_객체_키를_노출하지_않는다() throws Exception {
        Album album = saveAlbum("공개 범위 확인", LocalDate.of(2026, 10, 1), 1);

        mockMvc.perform(get("/api/v1/albums"))
                .andExpect(jsonPath("$.content[0].uploaderId").doesNotExist())
                .andExpect(jsonPath("$.content[0].objectKey").doesNotExist());
        mockMvc.perform(get("/api/v1/albums/{albumId}", album.getId()))
                .andExpect(jsonPath("$.uploaderId").doesNotExist())
                .andExpect(jsonPath("$.photos[0].objectKey").doesNotExist());
    }

    @Test
    void 로그인하지_않아도_상세를_보고_사진은_순서대로_온다() throws Exception {
        Album album = saveAlbum("10월 정기 세미나", LocalDate.of(2026, 10, 18), 3);

        mockMvc.perform(get("/api/v1/albums/{albumId}", album.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(album.getId()))
                .andExpect(jsonPath("$.title").value("10월 정기 세미나"))
                .andExpect(jsonPath("$.eventDate").value("2026-10-18"))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(ISO_UTC)))
                .andExpect(jsonPath("$.photos", hasSize(3)))
                .andExpect(jsonPath("$.photos[0].order").value(0))
                .andExpect(jsonPath("$.photos[1].order").value(1))
                .andExpect(jsonPath("$.photos[2].order").value(2))
                .andExpect(
                        jsonPath("$.photos[2].url")
                                .value(
                                        allOf(
                                                startsWith(
                                                        "https://cdn.local.invalid/"
                                                                + objectKey(album, 2)),
                                                containsString("Signature="))));
    }

    @Test
    void 없는_앨범은_404다() throws Exception {
        mockMvc.perform(get("/api/v1/albums/{albumId}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("GALLERY_ALBUM_NOT_FOUND"));
    }

    @Test
    void 앨범_ID가_숫자가_아니면_400이다() throws Exception {
        mockMvc.perform(get("/api/v1/albums/{albumId}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    /** 사진을 일부러 역순으로 저장해서, 응답 순서가 저장 순서가 아니라 display_order 기준인지 확인한다. */
    private Album saveAlbum(String title, LocalDate eventDate, int photoCount) {
        Album album = albumRepository.save(Album.create(title, eventDate, 1L));
        for (int order = photoCount - 1; order >= 0; order--) {
            photoRepository.save(
                    Photo.create(album.getId(), objectKey(album, order), (short) order));
        }
        return album;
    }

    private static String objectKey(Album album, int order) {
        return "gallery/album-" + album.getId() + "-photo-" + order + ".jpg";
    }
}
