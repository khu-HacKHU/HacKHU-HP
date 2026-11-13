package com.hackhu.hp.domain.gallery.controller;

import com.hackhu.hp.domain.gallery.dto.AlbumDetailResponse;
import com.hackhu.hp.domain.gallery.dto.AlbumPageResponse;
import com.hackhu.hp.domain.gallery.service.AlbumQueryService;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "갤러리", description = "앨범 조회. 공개 API")
@RestController
@RequestMapping("/api/v1/albums")
@RequiredArgsConstructor
public class AlbumController {

    /** 허용하는 정렬은 이것 하나뿐이다 (4-2 1절). */
    private static final Sort ALLOWED_SORT = Sort.by(Sort.Direction.DESC, "eventDate");

    private final AlbumQueryService albumQueryService;

    @Operation(
            summary = "앨범 목록",
            description =
                    "공개. 활동 날짜 최근순. size는 최대 100이고 넘으면 100으로 맞춘다."
                            + " sort는 생략하거나 eventDate,desc만 보낼 수 있다.")
    @GetMapping
    public AlbumPageResponse getAlbums(
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        // 다른 정렬 값을 그대로 쿼리에 넘기면 없는 필드에서 500이 나고 ERROR 로그가 쌓인다.
        // 화이트리스트로 먼저 막고, 실제 정렬은 서비스가 고정값으로 건다 (secure-coding 3절, CWE-89).
        Sort sort = pageable.getSort();
        if (sort.isSorted() && !sort.equals(ALLOWED_SORT)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return albumQueryService.getAlbums(pageable.getPageNumber(), pageable.getPageSize());
    }

    @Operation(summary = "앨범 상세", description = "공개. 사진은 order 오름차순. 없는 앨범은 404")
    @GetMapping("/{albumId}")
    public AlbumDetailResponse getAlbum(@PathVariable Long albumId) {
        return albumQueryService.getAlbum(albumId);
    }
}
