package com.hackhu.hp.domain.gallery.service;

import com.hackhu.hp.domain.gallery.dto.AlbumCreateRequest;
import com.hackhu.hp.domain.gallery.dto.AlbumDetailResponse;
import com.hackhu.hp.domain.gallery.dto.PhotoResponse;
import com.hackhu.hp.domain.gallery.entity.Album;
import com.hackhu.hp.domain.gallery.entity.Photo;
import com.hackhu.hp.domain.gallery.exception.GalleryErrorCode;
import com.hackhu.hp.domain.gallery.repository.AlbumRepository;
import com.hackhu.hp.domain.gallery.repository.PhotoRepository;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.storage.UploadConfirmService;
import com.hackhu.hp.global.storage.UploadPurpose;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 앨범 올리기 (docs/spec/gallery/backend/4-2 4절). */
@Service
public class AlbumCommandService {

    /**
     * 활동 날짜의 "오늘"은 한국 시간 기준이다. 서버가 UTC로 돌면 한국 시간 자정부터 오전 9시 사이에는 UTC로 아직 어제라서, 사용자가 자기 기준 오늘을 골랐는데
     * 미래로 거부당한다. 행사 끝나고 밤에 올리는 경우가 흔한 구간이다 (5-3 5-3-3).
     */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final UploadConfirmService uploadConfirmService;
    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final PhotoUrlResolver photoUrlResolver;

    public AlbumCommandService(
            UploadConfirmService uploadConfirmService,
            AlbumRepository albumRepository,
            PhotoRepository photoRepository,
            PhotoUrlResolver photoUrlResolver) {
        this.uploadConfirmService = uploadConfirmService;
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.photoUrlResolver = photoUrlResolver;
    }

    /**
     * 업로드를 확정하고 앨범을 만든다. 요청 순서가 사진 순서이고 0번째가 대표 사진이다.
     *
     * <p>확정(소유자·만료·중복·매직바이트 검사)은 {@link UploadConfirmService}가 하고, 실패하면 올라온 객체를 정리한 뒤 예외를 던진다. 그래서
     * 여기서는 통과한 키만 받는다.
     */
    @Transactional
    public AlbumDetailResponse create(long uploaderId, AlbumCreateRequest request) {
        validateEventDate(request.eventDate());

        List<String> objectKeys =
                uploadConfirmService.confirmImages(
                        uploaderId, UploadPurpose.GALLERY, request.uploadIds());

        Album album =
                albumRepository.save(
                        Album.create(request.title(), request.eventDate(), uploaderId));

        List<Photo> photos = new ArrayList<>();
        for (int order = 0; order < objectKeys.size(); order++) {
            photos.add(Photo.create(album.getId(), objectKeys.get(order), (short) order));
        }
        photoRepository.saveAll(photos);

        List<PhotoResponse> photoResponses =
                photos.stream()
                        .map(
                                photo ->
                                        new PhotoResponse(
                                                photo.getId(),
                                                photoUrlResolver.resolve(photo.getObjectKey()),
                                                photo.getDisplayOrder()))
                        .toList();
        return AlbumDetailResponse.of(album, photoResponses);
    }

    private void validateEventDate(LocalDate eventDate) {
        if (eventDate.isAfter(LocalDate.now(SERVICE_ZONE))) {
            throw new BusinessException(GalleryErrorCode.FUTURE_EVENT_DATE);
        }
    }
}
