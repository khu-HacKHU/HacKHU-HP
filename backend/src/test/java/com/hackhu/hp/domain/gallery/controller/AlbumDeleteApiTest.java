package com.hackhu.hp.domain.gallery.controller;

import static com.hackhu.hp.global.auth.LoginUserRequests.loginUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.domain.gallery.exception.GalleryErrorCode;
import com.hackhu.hp.domain.gallery.service.AlbumCommandService;
import com.hackhu.hp.domain.gallery.service.AlbumQueryService;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.error.GlobalExceptionHandler;
import com.hackhu.hp.global.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 앨범 삭제 API의 권한 거부 (#59). 비로그인 401과 승인 대기 회원 403은 SecurityConfig가, 남의 앨범 403은 서비스가 한다 (5-1 1절).
 * 서비스는 대역으로 두고 응답 형식과 "서비스까지 오지 않음"을 본다. 실제 삭제는 {@link AlbumDeleteIntegrationTest}가 본다.
 */
@WebMvcTest(controllers = AlbumController.class)
@Import({AlbumController.class, GlobalExceptionHandler.class, SecurityConfig.class})
class AlbumDeleteApiTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AlbumQueryService albumQueryService;
    @MockitoBean AlbumCommandService albumCommandService;

    @Test
    void 비로그인은_401이고_서비스까지_오지_않는다() throws Exception {
        mockMvc.perform(delete("/api/v1/albums/{albumId}", 12))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verify(albumCommandService, never()).delete(anyLong(), any());
    }

    @Test
    void 승인_대기_회원은_403이고_서비스까지_오지_않는다() throws Exception {
        mockMvc.perform(delete("/api/v1/albums/{albumId}", 12).with(loginUser(7L, Role.PENDING)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(albumCommandService, never()).delete(anyLong(), any());
    }

    @Test
    void 동아리원이면_서비스에_현재_사용자를_넘기고_204다() throws Exception {
        mockMvc.perform(delete("/api/v1/albums/{albumId}", 12).with(loginUser(7L, Role.MEMBER)))
                .andExpect(status().isNoContent());

        // 사용자는 요청 본문이 아니라 인증 정보에서 꺼낸다 (CWE-915).
        verify(albumCommandService).delete(eq(12L), eq(new LoginUser(7L, Role.MEMBER)));
    }

    @Test
    void 남의_앨범이면_403_GALLERY_FORBIDDEN이다() throws Exception {
        doThrow(new BusinessException(GalleryErrorCode.FORBIDDEN))
                .when(albumCommandService)
                .delete(anyLong(), any());

        mockMvc.perform(delete("/api/v1/albums/{albumId}", 12).with(loginUser(8L, Role.MEMBER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GALLERY_FORBIDDEN"));
    }

    @Test
    void 없는_앨범이면_404다() throws Exception {
        doThrow(new BusinessException(GalleryErrorCode.ALBUM_NOT_FOUND))
                .when(albumCommandService)
                .delete(anyLong(), any());

        mockMvc.perform(delete("/api/v1/albums/{albumId}", 999).with(loginUser(7L, Role.MEMBER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GALLERY_ALBUM_NOT_FOUND"));
    }
}
