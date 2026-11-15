package com.hackhu.hp.domain.gallery.controller;

import com.hackhu.hp.domain.gallery.dto.AlbumCreateRequest;
import com.hackhu.hp.domain.gallery.dto.AlbumDetailResponse;
import com.hackhu.hp.domain.gallery.dto.UploadUrlRequest;
import com.hackhu.hp.domain.gallery.dto.UploadUrlResponse;
import com.hackhu.hp.domain.gallery.service.AlbumCommandService;
import com.hackhu.hp.domain.gallery.service.GalleryUploadService;
import com.hackhu.hp.global.auth.CurrentUser;
import com.hackhu.hp.global.auth.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 앨범 올리기 흐름 (docs/spec/gallery/backend/4-2 3, 4절).
 *
 * <p>두 API 모두 동아리원 이상만 쓴다. 역할 검사는 {@code SecurityConfig}의 기본값(`anyRequest().hasRole(MEMBER)`)이 하므로
 * 여기서 다시 하지 않는다. 비로그인은 컨트롤러에 오기 전에 401, 승인 대기 회원은 403으로 끝난다 (auth 3-1-1 역할 검사).
 */
@RestController
@RequestMapping("/api/v1/albums")
public class GalleryUploadController {

    private final GalleryUploadService galleryUploadService;
    private final AlbumCommandService albumCommandService;

    public GalleryUploadController(
            GalleryUploadService galleryUploadService, AlbumCommandService albumCommandService) {
        this.galleryUploadService = galleryUploadService;
        this.albumCommandService = albumCommandService;
    }

    @Operation(summary = "업로드 주소 발급", description = "사진을 S3에 직접 올릴 presigned PUT 주소를 받는다.")
    @PostMapping("/upload-urls")
    public UploadUrlResponse issueUploadUrls(
            @CurrentUser LoginUser user, @Valid @RequestBody UploadUrlRequest request) {
        return galleryUploadService.issueUploadUrls(user.id(), request);
    }

    @Operation(summary = "앨범 올리기", description = "발급받은 uploadId들을 확정해 앨범을 만든다.")
    @PostMapping
    public ResponseEntity<AlbumDetailResponse> createAlbum(
            @CurrentUser LoginUser user, @Valid @RequestBody AlbumCreateRequest request) {
        AlbumDetailResponse response = albumCommandService.create(user.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/albums/" + response.id())).body(response);
    }
}
