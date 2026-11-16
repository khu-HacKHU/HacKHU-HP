package com.hackhu.hp.domain.gallery.controller;

import static com.hackhu.hp.global.auth.LoginUserRequests.loginUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.TestcontainersConfiguration;
import com.hackhu.hp.domain.gallery.entity.Album;
import com.hackhu.hp.domain.gallery.entity.Photo;
import com.hackhu.hp.domain.gallery.repository.AlbumRepository;
import com.hackhu.hp.domain.gallery.repository.PhotoRepository;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.storage.S3ObjectStore;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 앨범 삭제 (#59). 실제 PostgreSQL로 앨범과 사진 행이 지워지는지, S3 실패 때 롤백되는지 본다. S3는 대역으로 둔다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AlbumDeleteIntegrationTest {

    private static final long UPLOADER = 7L;

    @Autowired MockMvc mockMvc;
    @Autowired AlbumRepository albumRepository;
    @Autowired PhotoRepository photoRepository;

    @MockitoBean S3ObjectStore objectStore;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
    }

    @Test
    void 작성자가_지우면_앨범과_사진_행이_사라지고_S3_객체도_지운다() throws Exception {
        Album album = saveAlbum(2);

        mockMvc.perform(
                        delete("/api/v1/albums/{albumId}", album.getId())
                                .with(loginUser(UPLOADER, Role.MEMBER)))
                .andExpect(status().isNoContent());

        assertFalse(albumRepository.existsById(album.getId()));
        // 사진 행은 ON DELETE CASCADE로 같이 지워진다 (3-2 3절).
        assertEquals(0, photoRepository.findByAlbumIdOrderByDisplayOrderAsc(album.getId()).size());
        verify(objectStore).deleteAll(List.of(objectKey(album, 0), objectKey(album, 1)));
        // 목록에서도 바로 사라진다 (USER-STORIES 3절).
        mockMvc.perform(get("/api/v1/albums")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void 관리자는_남의_앨범을_지운다() throws Exception {
        Album album = saveAlbum(1);

        mockMvc.perform(
                        delete("/api/v1/albums/{albumId}", album.getId())
                                .with(loginUser(1L, Role.ADMIN)))
                .andExpect(status().isNoContent());

        assertFalse(albumRepository.existsById(album.getId()));
    }

    @Test
    void 다른_동아리원은_403이고_아무것도_지워지지_않는다() throws Exception {
        Album album = saveAlbum(1);

        mockMvc.perform(
                        delete("/api/v1/albums/{albumId}", album.getId())
                                .with(loginUser(8L, Role.MEMBER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GALLERY_FORBIDDEN"));

        assertTrue(albumRepository.existsById(album.getId()));
        verify(objectStore, never()).deleteAll(anyList());
    }

    @Test
    void S3_삭제가_실패하면_500이고_앨범은_남는다() throws Exception {
        Album album = saveAlbum(1);
        doThrow(new IllegalStateException("S3 장애")).when(objectStore).deleteAll(anyList());

        mockMvc.perform(
                        delete("/api/v1/albums/{albumId}", album.getId())
                                .with(loginUser(UPLOADER, Role.MEMBER)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        // 트랜잭션이 롤백되어 다시 지울 수 있다 (5-2 5-2-2).
        assertTrue(albumRepository.existsById(album.getId()));
        assertEquals(1, photoRepository.findByAlbumIdOrderByDisplayOrderAsc(album.getId()).size());
    }

    private Album saveAlbum(int photoCount) {
        Album album = albumRepository.save(Album.create("MT", LocalDate.of(2026, 9, 1), UPLOADER));
        for (int order = 0; order < photoCount; order++) {
            photoRepository.save(
                    Photo.create(album.getId(), objectKey(album, order), (short) order));
        }
        return album;
    }

    private static String objectKey(Album album, int order) {
        return "gallery/album-" + album.getId() + "-photo-" + order + ".jpg";
    }
}
