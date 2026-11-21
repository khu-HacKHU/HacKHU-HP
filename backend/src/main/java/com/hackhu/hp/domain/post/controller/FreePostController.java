package com.hackhu.hp.domain.post.controller;

import com.hackhu.hp.domain.post.dto.PostDetailResponse;
import com.hackhu.hp.domain.post.dto.PostPageResponse;
import com.hackhu.hp.domain.post.dto.PostWriteRequest;
import com.hackhu.hp.domain.post.service.FreePostService;
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
 * 자유게시판 API (docs/spec/board/backend/4-2-INTERACTION-CONTRACT.md 4-2-8, DECISIONS.md B5).
 *
 * <p>게시판 종류는 요청에서 받지 않는다. 서비스가 {@code FREE} 조건으로만 조회한다 (5-1-4). 공개 목록에 넣지 않으므로 Security 설정의
 * 기본값(동아리원 이상)이 모든 경로에 걸린다 (5-1-1).
 */
@RestController
@RequestMapping("/api/v1/free-posts")
@RequiredArgsConstructor
public class FreePostController {

    /** 허용하는 정렬은 이것 하나뿐이다 (4-2-3). */
    private static final String ALLOWED_SORT = "createdAt,desc";

    private final FreePostService freePostService;

    @Operation(
            summary = "자유게시판 목록",
            description =
                    "동아리원. 최신순. size는 1 이상이고 20을 넘으면 20으로 맞춘다. sort는 생략하거나 createdAt,desc만 보낼 수 있다.")
    @GetMapping
    public PostPageResponse getPosts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size,
            @RequestParam(required = false) String sort) {
        // 다른 정렬 값은 쿼리를 실행하기 전에 거부한다. 실제 정렬은 서버가 고정값으로 건다 (5-1-5, CWE-89).
        if (sort != null && !sort.equals(ALLOWED_SORT)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return freePostService.getPosts(page, size);
    }

    @Operation(summary = "자유게시판 상세", description = "동아리원. 없는 글과 다른 게시판 글은 404")
    @GetMapping("/{postId}")
    public PostDetailResponse getPost(@PathVariable @Positive Long postId) {
        return freePostService.getPost(postId);
    }

    @Operation(summary = "자유게시판 작성", description = "동아리원. 작성자는 로그인한 사람이다")
    @PostMapping
    public ResponseEntity<PostDetailResponse> createPost(
            @CurrentUser LoginUser user, @Valid @RequestBody PostWriteRequest request) {
        PostDetailResponse response = freePostService.create(user, request);
        return ResponseEntity.created(URI.create("/api/v1/free-posts/" + response.id()))
                .body(response);
    }

    @Operation(summary = "자유게시판 수정", description = "작성자 또는 관리자. 제목과 본문을 모두 보낸다")
    @PutMapping("/{postId}")
    public PostDetailResponse updatePost(
            @CurrentUser LoginUser user,
            @PathVariable @Positive Long postId,
            @Valid @RequestBody PostWriteRequest request) {
        return freePostService.update(user, postId, request);
    }

    @Operation(summary = "자유게시판 삭제", description = "작성자 또는 관리자. 복구할 수 없다")
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @CurrentUser LoginUser user, @PathVariable @Positive Long postId) {
        freePostService.delete(user, postId);
        return ResponseEntity.noContent().build();
    }
}
