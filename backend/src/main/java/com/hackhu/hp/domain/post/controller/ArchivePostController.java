package com.hackhu.hp.domain.post.controller;

import com.hackhu.hp.domain.post.dto.ArchivePostCreateRequest;
import com.hackhu.hp.domain.post.dto.ArchivePostDetailResponse;
import com.hackhu.hp.domain.post.dto.ArchivePostPageResponse;
import com.hackhu.hp.domain.post.dto.ArchivePostUpdateRequest;
import com.hackhu.hp.domain.post.dto.AttachmentDownloadUrlResponse;
import com.hackhu.hp.domain.post.dto.AttachmentUploadUrlRequest;
import com.hackhu.hp.domain.post.dto.AttachmentUploadUrlResponse;
import com.hackhu.hp.domain.post.service.ArchivePostService;
import com.hackhu.hp.global.auth.CurrentUser;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자료게시판 API (docs/spec/board/backend/4-2-INTERACTION-CONTRACT.md 4-2-9 ~ 4-2-11, DECISIONS.md B5).
 *
 * <p>공개 목록에 넣지 않으므로 Security 설정의 기본값(동아리원 이상)이 모든 경로에 걸린다 (5-1-1). 게시판 종류는 요청에서 받지 않는다 (5-1-4).
 */
@RestController
@RequestMapping("/api/v1/archive-posts")
@RequiredArgsConstructor
public class ArchivePostController {

    private static final String ALLOWED_SORT = "createdAt,desc";

    private final ArchivePostService archivePostService;

    @Operation(summary = "자료게시판 목록", description = "동아리원. 최신순. 항목마다 첨부 수가 있다")
    @GetMapping
    public ArchivePostPageResponse getPosts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) String sort) {
        // 다른 정렬 값은 쿼리를 실행하기 전에 거부한다 (5-1-5, CWE-89).
        if (sort != null && !sort.equals(ALLOWED_SORT)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return archivePostService.getPosts(page, size);
    }

    @Operation(summary = "자료게시판 상세", description = "동아리원. 다운로드 주소는 넣지 않는다")
    @GetMapping("/{postId}")
    public ArchivePostDetailResponse getPost(@PathVariable @Positive Long postId) {
        return archivePostService.getPost(postId);
    }

    @Operation(summary = "자료게시판 작성", description = "동아리원. uploadIds 순서가 첨부 표시 순서다")
    @PostMapping
    public ResponseEntity<ArchivePostDetailResponse> createPost(
            @CurrentUser LoginUser user, @Valid @RequestBody ArchivePostCreateRequest request) {
        ArchivePostDetailResponse response = archivePostService.create(user, request);
        return ResponseEntity.created(URI.create("/api/v1/archive-posts/" + response.id()))
                .body(response);
    }

    @Operation(
            summary = "자료게시판 수정",
            description = "작성자 또는 관리자. attachmentIds에 없는 기존 첨부는 지운다. 합쳐서 5개까지")
    @PutMapping("/{postId}")
    public ArchivePostDetailResponse updatePost(
            @CurrentUser LoginUser user,
            @PathVariable @Positive Long postId,
            @Valid @RequestBody ArchivePostUpdateRequest request) {
        return archivePostService.update(user, postId, request);
    }

    @Operation(summary = "자료게시판 삭제", description = "작성자 또는 관리자. 첨부파일도 함께 지운다")
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @CurrentUser LoginUser user, @PathVariable @Positive Long postId) {
        archivePostService.delete(user, postId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "첨부 업로드 주소 발급",
            description = "동아리원. pdf, docx, pptx, xlsx, 파일당 20MB, 한 번에 5개. 주소는 10분 뒤 만료")
    @PostMapping("/upload-urls")
    public AttachmentUploadUrlResponse issueUploadUrls(
            @CurrentUser LoginUser user, @Valid @RequestBody AttachmentUploadUrlRequest request) {
        return archivePostService.issueUploadUrls(user, request);
    }

    @Operation(summary = "첨부 다운로드 주소 발급", description = "동아리원. 받기를 누를 때만 부른다. 주소는 10분 뒤 만료")
    @PostMapping("/{postId}/attachments/{attachmentId}/download-url")
    public AttachmentDownloadUrlResponse issueDownloadUrl(
            @CurrentUser LoginUser user,
            @PathVariable @Positive Long postId,
            @PathVariable @Positive Long attachmentId) {
        return archivePostService.issueDownloadUrl(user, postId, attachmentId);
    }
}
