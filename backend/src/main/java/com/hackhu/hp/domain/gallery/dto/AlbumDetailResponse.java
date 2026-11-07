package com.hackhu.hp.domain.gallery.dto;

import com.hackhu.hp.domain.gallery.entity.Album;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 앨범 상세. 작성자 정보는 넣지 않는다 (4-2 2절). */
@Schema(description = "앨범 상세")
public record AlbumDetailResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "10월 정기 세미나") String title,
        @Schema(example = "2026-10-18") LocalDate eventDate,
        @Schema(description = "order 오름차순") List<PhotoResponse> photos,
        @Schema(example = "2026-10-19T09:00:00Z") Instant createdAt) {

    public static AlbumDetailResponse of(Album album, List<PhotoResponse> photos) {
        return new AlbumDetailResponse(
                album.getId(),
                album.getTitle(),
                album.getEventDate(),
                photos,
                album.getCreatedAt());
    }
}
