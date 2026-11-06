package com.hackhu.hp.domain.gallery.controller;

import com.hackhu.hp.domain.gallery.dto.UploadUrlRequest;
import com.hackhu.hp.domain.gallery.dto.UploadUrlResponse;
import com.hackhu.hp.domain.gallery.service.GalleryUploadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/albums")
public class GalleryUploadController {

    private final GalleryUploadService galleryUploadService;

    public GalleryUploadController(GalleryUploadService galleryUploadService) {
        this.galleryUploadService = galleryUploadService;
    }

    @PostMapping("/upload-urls")
    public UploadUrlResponse issueUploadUrls(@Valid @RequestBody UploadUrlRequest request) {
        // TODO(#22): 인증 정보에서 현재 사용자 ID를 꺼내고, 동아리원 이상인지 검사한다.
        // #22(현재 사용자와 역할 확인)가 머지되면 이 한 줄만 교체한다. 그 전까지 이 API는 인증이 없어 쓸 수 없다.
        Long userId = 0L;
        return galleryUploadService.issueUploadUrls(userId, request);
    }
}
