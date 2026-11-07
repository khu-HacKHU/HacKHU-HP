package com.hackhu.hp.domain.gallery.dto;

import com.hackhu.hp.domain.gallery.entity.Album;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** 앨범 목록의 한 줄. 작성자 정보는 넣지 않는다 (4-2 2절). */
@Schema(description = "앨범 목록 항목")
public record AlbumSummaryResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "10월 정기 세미나") String title,
        @Schema(example = "2026-10-18") LocalDate eventDate,
        @Schema(description = "대표 사진의 CloudFront 서명 URL. 1시간 뒤 만료된다", nullable = true)
                String coverPhotoUrl,
        @Schema(example = "2026-10-19T09:00:00Z") Instant createdAt) {

    public static AlbumSummaryResponse of(Album album, String coverPhotoUrl) {
        return new AlbumSummaryResponse(
                album.getId(),
                album.getTitle(),
                album.getEventDate(),
                coverPhotoUrl,
                album.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
