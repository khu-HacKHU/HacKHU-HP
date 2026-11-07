package com.hackhu.hp.domain.gallery.service;

import com.hackhu.hp.domain.gallery.dto.AlbumDetailResponse;
import com.hackhu.hp.domain.gallery.dto.AlbumPageResponse;
import com.hackhu.hp.domain.gallery.dto.AlbumSummaryResponse;
import com.hackhu.hp.domain.gallery.dto.PhotoResponse;
import com.hackhu.hp.domain.gallery.entity.Album;
import com.hackhu.hp.domain.gallery.entity.Photo;
import com.hackhu.hp.domain.gallery.exception.GalleryErrorCode;
import com.hackhu.hp.domain.gallery.repository.AlbumRepository;
import com.hackhu.hp.domain.gallery.repository.PhotoRepository;
import com.hackhu.hp.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 앨범 목록과 상세 조회. 둘 다 공개 API라 권한 검사가 없다 (5-1 1절). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlbumQueryService {

    /**
     * 활동 날짜 최근순. 같은 날짜끼리는 id로 순서를 고정해서 페이지를 넘길 때 앨범이 겹치거나 빠지지 않게 한다. 정렬 기준은 서버가 정하고 클라이언트 값은 쓰지 않는다
     * (secure-coding 3절).
     */
    private static final Sort LIST_SORT =
            Sort.by(Sort.Order.desc("eventDate"), Sort.Order.desc("id"));

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final PhotoUrlResolver photoUrlResolver;

    public AlbumPageResponse getAlbums(int page, int size) {
        Page<Album> albums = albumRepository.findAll(PageRequest.of(page, size, LIST_SORT));
        Map<Long, String> coverKeys = coverObjectKeys(albums.getContent());
        Page<AlbumSummaryResponse> content =
                albums.map(
                        album ->
                                AlbumSummaryResponse.of(
                                        album, toUrl(coverKeys.get(album.getId()))));
        return AlbumPageResponse.from(content);
    }

    public AlbumDetailResponse getAlbum(Long albumId) {
        Album album =
                albumRepository
                        .findById(albumId)
                        .orElseThrow(() -> new BusinessException(GalleryErrorCode.ALBUM_NOT_FOUND));
        List<PhotoResponse> photos =
                photoRepository.findByAlbumIdOrderByDisplayOrderAsc(albumId).stream()
                        .map(
                                photo ->
                                        new PhotoResponse(
                                                photo.getId(),
                                                photoUrlResolver.resolve(photo.getObjectKey()),
                                                photo.getDisplayOrder()))
                        .toList();
        return AlbumDetailResponse.of(album, photos);
    }

    private Map<Long, String> coverObjectKeys(List<Album> albums) {
        if (albums.isEmpty()) {
            return Map.of();
        }
        List<Long> albumIds = albums.stream().map(Album::getId).toList();
        return photoRepository.findByAlbumIdInAndDisplayOrder(albumIds, Photo.COVER_ORDER).stream()
                .collect(Collectors.toMap(Photo::getAlbumId, Photo::getObjectKey));
    }

    // 앨범은 사진이 1장 이상이라(D9) 대표 사진이 없을 일은 없지만, 데이터가 어긋나도 목록 전체가 깨지지 않게 null을 준다.
    private String toUrl(String objectKey) {
        return objectKey == null ? null : photoUrlResolver.resolve(objectKey);
    }
}
