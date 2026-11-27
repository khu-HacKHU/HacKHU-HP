package com.hackhu.hp.domain.gallery.controller;

import static com.hackhu.hp.global.auth.LoginUserRequests.loginUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.domain.user.repository.UserRepository;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.storage.S3ObjectStore;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    /** 앨범을 올린 사람. album.uploader_id에 FK가 걸려 있어 실제 users 행이 있어야 한다 (#143). */
    private long uploader;

    @Autowired MockMvc mockMvc;
    @Autowired AlbumRepository albumRepository;
    @Autowired PhotoRepository photoRepository;
    @Autowired UserRepository userRepository;

    @MockitoBean S3ObjectStore objectStore;

    @BeforeEach
    void setUp() {
        photoRepository.deleteAllInBatch();
        albumRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        uploader = userRepository.save(User.signUp("uploader@khu.ac.kr", "올린사람", "hash")).getId();
    }

    @Test
    void 작성자가_지우면_앨범과_사진_행이_사라지고_S3_객체도_지운다() throws Exception {
        Album album = saveAlbum(2);

        mockMvc.perform(
                        delete("/api/v1/albums/{albumId}", album.getId())
                                .with(loginUser(uploader, Role.MEMBER)))
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
                                .with(loginUser(uploader, Role.MEMBER)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        // 트랜잭션이 롤백되어 다시 지울 수 있다 (5-2 5-2-2).
        assertTrue(albumRepository.existsById(album.getId()));
        assertEquals(1, photoRepository.findByAlbumIdOrderByDisplayOrderAsc(album.getId()).size());
    }

    @Test
    void 같은_앨범을_동시에_두_번_지우면_늦은_쪽은_404다() throws Exception {
        Album album = saveAlbum(1);
        CountDownLatch firstInS3 = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        // 앞 요청을 S3 삭제 단계에 붙잡아 둔다. 이때 앨범 행은 지워졌지만 커밋 전이라 행 잠금이 걸려 있다.
        doAnswer(
                        invocation -> {
                            firstInS3.countDown();
                            releaseFirst.await(10, TimeUnit.SECONDS);
                            return null;
                        })
                .when(objectStore)
                .deleteAll(anyList());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> deleteStatus(album.getId()));
            firstInS3.await(10, TimeUnit.SECONDS);
            // 늦은 요청은 커밋 전이라 앨범을 조회하고 권한 검사를 통과한 뒤, 행 잠금에서 기다린다.
            Future<Integer> second = executor.submit(() -> deleteStatus(album.getId()));
            Thread.sleep(500);
            releaseFirst.countDown();

            assertEquals(204, first.get(10, TimeUnit.SECONDS));
            // 500이 아니라 문서가 정한 404여야 한다 (5-2 5-2-2, 5-3 5-3-7).
            assertEquals(404, second.get(10, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
        assertFalse(albumRepository.existsById(album.getId()));
        // S3 삭제는 먼저 지운 요청만 한다.
        verify(objectStore, times(1)).deleteAll(anyList());
    }

    private int deleteStatus(long albumId) throws Exception {
        return mockMvc.perform(
                        delete("/api/v1/albums/{albumId}", albumId)
                                .with(loginUser(uploader, Role.MEMBER)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private Album saveAlbum(int photoCount) {
        Album album = albumRepository.save(Album.create("MT", LocalDate.of(2026, 9, 1), uploader));
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
