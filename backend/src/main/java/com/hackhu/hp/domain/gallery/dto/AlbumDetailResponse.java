package com.hackhu.hp.domain.gallery.dto;

import com.hackhu.hp.domain.gallery.entity.Album;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 앨범 상세. 작성자 ID 대신 서버가 판단한 삭제 가능 여부만 준다 (4-2 2절). */
@Schema(description = "앨범 상세")
public record AlbumDetailResponse(
        @Schema(example = "12") Long id,
        @Schema(example = "10월 정기 세미나") String title,
        @Schema(example = "2026-10-18") LocalDate eventDate,
        @Schema(description = "현재 사용자가 이 앨범을 지울 수 있는지. 화면 표시용이고 삭제 API가 권한을 다시 검사한다. 비로그인이면 false")
                boolean canDelete,
        @Schema(description = "order 오름차순") List<PhotoResponse> photos,
        @Schema(example = "2026-10-19T09:00:00Z") Instant createdAt) {

    public static AlbumDetailResponse of(
            Album album, boolean canDelete, List<PhotoResponse> photos) {
        return new AlbumDetailResponse(
                album.getId(),
                album.getTitle(),
                album.getEventDate(),
                canDelete,
                photos,
                album.getCreatedAt());
    }
}
