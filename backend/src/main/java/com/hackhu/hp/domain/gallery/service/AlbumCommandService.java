package com.hackhu.hp.domain.gallery.service;

import com.hackhu.hp.domain.gallery.dto.AlbumCreateRequest;
import com.hackhu.hp.domain.gallery.dto.AlbumDetailResponse;
import com.hackhu.hp.domain.gallery.dto.PhotoResponse;
import com.hackhu.hp.domain.gallery.entity.Album;
import com.hackhu.hp.domain.gallery.entity.Photo;
import com.hackhu.hp.domain.gallery.exception.GalleryErrorCode;
import com.hackhu.hp.domain.gallery.repository.AlbumRepository;
import com.hackhu.hp.domain.gallery.repository.PhotoRepository;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.storage.S3ObjectStore;
import com.hackhu.hp.global.storage.UploadConfirmService;
import com.hackhu.hp.global.storage.UploadPurpose;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 앨범 올리기와 삭제 (docs/spec/gallery/backend/4-2 4, 5절). */
@Slf4j
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
    private final S3ObjectStore objectStore;

    public AlbumCommandService(
            UploadConfirmService uploadConfirmService,
            AlbumRepository albumRepository,
            PhotoRepository photoRepository,
            PhotoUrlResolver photoUrlResolver,
            S3ObjectStore objectStore) {
        this.uploadConfirmService = uploadConfirmService;
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.photoUrlResolver = photoUrlResolver;
        this.objectStore = objectStore;
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
                        .map(photo ->
                                new PhotoResponse(
                                        photo.getId(),
                                        photoUrlResolver.resolve(photo.getObjectKey()),
                                        photo.getDisplayOrder()))
                        .toList();
        // 방금 올린 사람이 보는 응답이라 지울 수 있다 (동아리원 이상만 이 API에 들어온다).
        return AlbumDetailResponse.of(album, true, photoResponses);
    }

    /**
     * 앨범과 사진을 지운다 (4-2 5절). 사진 행은 DB의 ON DELETE CASCADE로 같이 지워진다 (3-2 3절).
     *
     * <p>순서는 DB 삭제, S3 삭제, 커밋이다 (5-2 5-2-2). S3 삭제가 실패하면 예외로 트랜잭션이 롤백되어 앨범이 남고, 사용자는 다시 지울 수 있다.
     * 반대로 DB를 커밋한 뒤 S3가 실패하면, 화면에서는 사라졌는데 받아 둔 사진 주소가 계속 열린다 (USER-STORIES 3절 위반). S3 삭제는 없는 키에도
     * 성공하므로 재시도가 안전하다.
     *
     * <p>권한은 요청마다 여기서 검사한다. 상세 응답의 {@code canDelete}는 화면 표시용이라 믿지 않는다 (CWE-639).
     *
     * <p>같은 앨범을 동시에 두 번 지우면 두 요청 모두 조회와 권한 검사를 통과할 수 있다. 늦은 요청은 앞 요청이 커밋할 때까지 행 잠금을 기다렸다가 0행을 지우므로,
     * 그때 404를 준다. 엔티티 삭제를 쓰면 이 경우가 낙관적 잠금 예외로 500이 되고 ERROR 로그가 쌓인다 (CWE-779).
     */
    @Transactional
    public void delete(long albumId, LoginUser user) {
        Album album =
                albumRepository
                        .findById(albumId)
                        .orElseThrow(() -> new BusinessException(GalleryErrorCode.ALBUM_NOT_FOUND));
        // 앨범은 공개 리소스라 존재를 숨기지 않는다. 없으면 404, 있는데 권한이 없으면 403 (4-2 5절).
        if (!AlbumPermission.canDelete(album, user)) {
            throw new BusinessException(GalleryErrorCode.FORBIDDEN);
        }

        List<String> objectKeys =
                photoRepository.findByAlbumIdOrderByDisplayOrderAsc(albumId).stream()
                        .map(Photo::getObjectKey)
                        .toList();

        // 조건부 삭제라 바로 실행된다. DB 쪽 실패는 S3를 지우기 전에 드러난다.
        if (albumRepository.deleteAlbumById(albumId) == 0) {
            // 다른 요청이 먼저 지웠다. S3는 그 요청이 지운다.
            throw new BusinessException(GalleryErrorCode.ALBUM_NOT_FOUND);
        }
        objectStore.deleteAll(objectKeys);

        // 관리자가 남의 앨범을 지우면 누가 무엇을 지웠는지 남긴다 (5-1 5절, secure-coding 1절).
        if (album.getUploaderId() != user.id()) {
            log.info(
                    "관리자가 다른 사람의 앨범을 삭제: adminId={}, albumId={}, uploaderId={}, photoCount={}",
                    user.id(),
                    albumId,
                    album.getUploaderId(),
                    objectKeys.size());
        }
    }

    private void validateEventDate(LocalDate eventDate) {
        if (eventDate.isAfter(LocalDate.now(SERVICE_ZONE))) {
            throw new BusinessException(GalleryErrorCode.FUTURE_EVENT_DATE);
        }
    }
}
