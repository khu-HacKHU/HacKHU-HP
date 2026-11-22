package com.hackhu.hp.domain.post.controller;

import com.hackhu.hp.domain.post.dto.NoticeDetailResponse;
import com.hackhu.hp.domain.post.dto.NoticePageResponse;
import com.hackhu.hp.domain.post.dto.NoticeWriteRequest;
import com.hackhu.hp.domain.post.service.NoticeCommandService;
import com.hackhu.hp.domain.post.service.NoticeQueryService;
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
 * 공지 API (docs/spec/board/backend/4-2-INTERACTION-CONTRACT.md).
 *
 * <p>게시판 종류는 요청에서 받지 않는다. 이 컨트롤러는 공지만 다루고, 서비스가 {@code NOTICE} 조건으로만 조회한다 (5-1-4).
 */
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    /** 허용하는 정렬은 이것 하나뿐이다 (4-2-3). */
    private static final String ALLOWED_SORT = "createdAt,desc";

    private final NoticeQueryService noticeQueryService;
    private final NoticeCommandService noticeCommandService;

    @Operation(
            summary = "공지 목록",
            description =
                    "공개. 최신순. size는 1 이상이고 20을 넘으면 20으로 맞춘다."
                            + " sort는 생략하거나 createdAt,desc만 보낼 수 있다.")
    @GetMapping
    public NoticePageResponse getNotices(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) String sort) {
        // 다른 정렬 값은 쿼리를 실행하기 전에 거부한다. 실제 정렬은 서비스가 고정값으로 건다 (5-1-5, CWE-89).
        if (sort != null && !sort.equals(ALLOWED_SORT)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return noticeQueryService.getNotices(page, size);
    }

    @Operation(summary = "공지 상세", description = "공개. 없는 공지와 다른 게시판 글은 404")
    @GetMapping("/{noticeId}")
    public NoticeDetailResponse getNotice(@PathVariable @Positive Long noticeId) {
        return noticeQueryService.getNotice(noticeId);
    }

    @Operation(summary = "공지 작성", description = "운영진. 작성자는 로그인한 사람이다")
    @PostMapping
    public ResponseEntity<NoticeDetailResponse> createNotice(
            @CurrentUser LoginUser user, @Valid @RequestBody NoticeWriteRequest request) {
        NoticeDetailResponse response = noticeCommandService.create(user, request);
        return ResponseEntity.created(URI.create("/api/v1/notices/" + response.id()))
                .body(response);
    }

    @Operation(summary = "공지 수정", description = "운영진이면서 작성자, 또는 관리자. 제목과 본문을 모두 보낸다")
    @PutMapping("/{noticeId}")
    public NoticeDetailResponse updateNotice(
            @CurrentUser LoginUser user,
            @PathVariable @Positive Long noticeId,
            @Valid @RequestBody NoticeWriteRequest request) {
        return noticeCommandService.update(user, noticeId, request);
    }

    @Operation(summary = "공지 삭제", description = "운영진이면서 작성자, 또는 관리자. 복구할 수 없다")
    @DeleteMapping("/{noticeId}")
    public ResponseEntity<Void> deleteNotice(
            @CurrentUser LoginUser user, @PathVariable @Positive Long noticeId) {
        noticeCommandService.delete(user, noticeId);
        return ResponseEntity.noContent().build();
    }
}
