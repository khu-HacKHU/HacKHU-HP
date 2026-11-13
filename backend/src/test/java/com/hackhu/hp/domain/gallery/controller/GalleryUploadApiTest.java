package com.hackhu.hp.domain.gallery.controller;

import static com.hackhu.hp.global.auth.LoginUserRequests.loginUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.domain.gallery.dto.UploadUrlResponse;
import com.hackhu.hp.domain.gallery.service.AlbumCommandService;
import com.hackhu.hp.domain.gallery.service.GalleryUploadService;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.error.GlobalExceptionHandler;
import com.hackhu.hp.global.security.SecurityConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 업로드 API의 권한 검사 (#40 완료 조건 "동아리원만 올릴 수 있음").
 *
 * <p>거부 케이스 확인이 목적이라 서비스는 대역으로 둔다. 비로그인은 401, 승인 대기 회원은 403으로 끝나고 서비스까지 오지 않아야 한다 (5-1 1절). 역할 검사는
 * {@link SecurityConfig}의 기본값이 하므로 그 설정을 같이 올린다.
 */
@WebMvcTest(controllers = GalleryUploadController.class)
@Import({GalleryUploadController.class, GlobalExceptionHandler.class, SecurityConfig.class})
class GalleryUploadApiTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean GalleryUploadService galleryUploadService;
    @MockitoBean AlbumCommandService albumCommandService;

    // 권한 검사만 보는 테스트라 본문은 형식만 맞춘 최소값으로 둔다.
    private static String uploadUrlsBody() {
        return "{\"files\":[{\"contentType\":\"image/jpeg\",\"size\":1024}]}";
    }

    private static String createAlbumBody() {
        return "{\"title\":\"10월 정기 세미나\",\"eventDate\":\""
                + LocalDate.now().minusDays(1)
                + "\",\"uploadIds\":[\""
                + UUID.randomUUID()
                + "\"]}";
    }

    @Test
    void 비로그인은_업로드_주소를_발급받지_못한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/albums/upload-urls")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(uploadUrlsBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(galleryUploadService, never()).issueUploadUrls(any(), any());
    }

    @Test
    void 승인_대기_회원은_업로드_주소를_발급받지_못한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/albums/upload-urls")
                                .with(loginUser(7L, Role.PENDING))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(uploadUrlsBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(galleryUploadService, never()).issueUploadUrls(any(), any());
    }

    @Test
    void 비로그인은_앨범을_올리지_못한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/albums")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createAlbumBody()))
                .andExpect(status().isUnauthorized());

        verify(albumCommandService, never()).create(anyLong(), any());
    }

    @Test
    void 승인_대기_회원은_앨범을_올리지_못한다() throws Exception {
        mockMvc.perform(
                        post("/api/v1/albums")
                                .with(loginUser(7L, Role.PENDING))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createAlbumBody()))
                .andExpect(status().isForbidden());

        verify(albumCommandService, never()).create(anyLong(), any());
    }

    @Test
    void 동아리원은_업로드_주소를_발급받는다() throws Exception {
        when(galleryUploadService.issueUploadUrls(any(), any()))
                .thenReturn(new UploadUrlResponse(List.of()));

        mockMvc.perform(
                        post("/api/v1/albums/upload-urls")
                                .with(loginUser(7L, Role.MEMBER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(uploadUrlsBody()))
                .andExpect(status().isOk());
    }

    @Test
    void 운영진도_업로드_주소를_발급받는다() throws Exception {
        when(galleryUploadService.issueUploadUrls(any(), any()))
                .thenReturn(new UploadUrlResponse(List.of()));

        mockMvc.perform(
                        post("/api/v1/albums/upload-urls")
                                .with(loginUser(7L, Role.STAFF))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(uploadUrlsBody()))
                .andExpect(status().isOk());
    }

    @Test
    void 업로더는_요청_본문이_아니라_인증_정보에서_꺼낸다() throws Exception {
        when(galleryUploadService.issueUploadUrls(any(), any()))
                .thenReturn(new UploadUrlResponse(List.of()));

        mockMvc.perform(
                        post("/api/v1/albums/upload-urls")
                                .with(loginUser(42L, Role.MEMBER))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(uploadUrlsBody()))
                .andExpect(status().isOk());

        verify(galleryUploadService).issueUploadUrls(eq(42L), any());
    }
}
