package com.hackhu.hp.domain.gallery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackhu.hp.domain.gallery.dto.AlbumCreateRequest;
import com.hackhu.hp.domain.gallery.dto.AlbumDetailResponse;
import com.hackhu.hp.domain.gallery.entity.Album;
import com.hackhu.hp.domain.gallery.entity.Photo;
import com.hackhu.hp.domain.gallery.repository.AlbumRepository;
import com.hackhu.hp.domain.gallery.repository.PhotoRepository;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.storage.S3ObjectStore;
import com.hackhu.hp.global.storage.UploadConfirmService;
import com.hackhu.hp.global.storage.UploadPurpose;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlbumCommandServiceTest {

    private static final long UPLOADER = 7L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private UploadConfirmService uploadConfirmService;
    private AlbumRepository albumRepository;
    private PhotoRepository photoRepository;
    private S3ObjectStore objectStore;
    private AlbumCommandService service;

    @BeforeEach
    void setUp() {
        uploadConfirmService = mock(UploadConfirmService.class);
        albumRepository = mock(AlbumRepository.class);
        photoRepository = mock(PhotoRepository.class);
        objectStore = mock(S3ObjectStore.class);
        PhotoUrlResolver photoUrlResolver = objectKey -> "https://cdn.example/" + objectKey;

        service =
                new AlbumCommandService(
                        uploadConfirmService,
                        albumRepository,
                        photoRepository,
                        photoUrlResolver,
                        objectStore);

        when(albumRepository.save(any(Album.class))).thenAnswer(i -> i.getArgument(0));
        when(photoRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    private AlbumCreateRequest request(LocalDate eventDate, int photoCount) {
        List<UUID> uploadIds =
                java.util.stream.Stream.generate(UUID::randomUUID).limit(photoCount).toList();
        return new AlbumCreateRequest("10월 정기 세미나", eventDate, uploadIds);
    }

    @Test
    void 확정된_키를_요청_순서대로_사진으로_만든다() {
        AlbumCreateRequest request = request(LocalDate.now(KST).minusDays(3), 3);
        when(uploadConfirmService.confirmImages(anyLong(), any(UploadPurpose.class), anyList()))
                .thenReturn(List.of("gallery/a", "gallery/b", "gallery/c"));

        AlbumDetailResponse response = service.create(UPLOADER, request);

        assertEquals(3, response.photos().size());
        // 0번째가 대표 사진이고, 순서가 요청 순서와 같아야 한다.
        assertEquals(0, response.photos().get(0).order());
        assertEquals(1, response.photos().get(1).order());
        assertEquals(2, response.photos().get(2).order());
        assertEquals("https://cdn.example/gallery/a", response.photos().get(0).url());
        assertEquals("https://cdn.example/gallery/c", response.photos().get(2).url());
    }

    @Test
    void 확정에_쓰는_용도는_갤러리다() {
        when(uploadConfirmService.confirmImages(anyLong(), any(UploadPurpose.class), anyList()))
                .thenReturn(List.of("gallery/a"));

        service.create(UPLOADER, request(LocalDate.now(KST), 1));

        verify(uploadConfirmService)
                .confirmImages(
                        org.mockito.ArgumentMatchers.eq(UPLOADER),
                        org.mockito.ArgumentMatchers.eq(UploadPurpose.GALLERY),
                        anyList());
    }

    @Test
    void 오늘_날짜는_받는다() {
        when(uploadConfirmService.confirmImages(anyLong(), any(UploadPurpose.class), anyList()))
                .thenReturn(List.of("gallery/a"));

        AlbumDetailResponse response = service.create(UPLOADER, request(LocalDate.now(KST), 1));

        assertEquals(1, response.photos().size());
    }

    @Test
    void 한국_시간_기준_내일_날짜는_거부한다() {
        AlbumCreateRequest request = request(LocalDate.now(KST).plusDays(1), 1);

        BusinessException thrown =
                assertThrows(BusinessException.class, () -> service.create(UPLOADER, request));

        assertEquals("GALLERY_FUTURE_EVENT_DATE", thrown.getErrorCode().code());
        // 날짜가 틀리면 업로드 확정도, 앨범 저장도 하지 않는다.
        verify(uploadConfirmService, never()).confirmImages(anyLong(), any(), anyList());
        verify(albumRepository, never()).save(any(Album.class));
    }

    @Test
    void 확정이_실패하면_앨범을_만들지_않는다() {
        when(uploadConfirmService.confirmImages(anyLong(), any(UploadPurpose.class), anyList()))
                .thenThrow(new com.hackhu.hp.global.storage.UploadConfirmException(List.of()));

        assertThrows(
                com.hackhu.hp.global.storage.UploadConfirmException.class,
                () -> service.create(UPLOADER, request(LocalDate.now(KST), 1)));

        verify(albumRepository, never()).save(any(Album.class));
        verify(photoRepository, never()).saveAll(anyList());
    }

    @Test
    void 사진_주소는_PhotoUrlResolver가_만든다() {
        when(uploadConfirmService.confirmImages(anyLong(), any(UploadPurpose.class), anyList()))
                .thenReturn(List.of("gallery/key"));

        AlbumDetailResponse response = service.create(UPLOADER, request(LocalDate.now(KST), 1));

        // 객체 키를 그대로 노출하지 않고 조회 주소로 바꿔서 준다 (4-2 2절).
        assertEquals("https://cdn.example/gallery/key", response.photos().get(0).url());
    }

    @Test
    void 앨범은_확정을_통과한_뒤에_저장한다() {
        when(uploadConfirmService.confirmImages(anyLong(), any(UploadPurpose.class), anyList()))
                .thenReturn(List.of("gallery/a"));

        service.create(UPLOADER, request(LocalDate.now(KST), 1));

        org.mockito.InOrder inOrder =
                org.mockito.Mockito.inOrder(uploadConfirmService, albumRepository, photoRepository);
        inOrder.verify(uploadConfirmService).confirmImages(anyLong(), any(), anyList());
        inOrder.verify(albumRepository).save(any(Album.class));
        inOrder.verify(photoRepository).saveAll(anyList());
    }

    @Test
    void 사진은_앨범_ID로_연결한다() {
        when(uploadConfirmService.confirmImages(anyLong(), any(UploadPurpose.class), anyList()))
                .thenReturn(List.of("gallery/a"));

        service.create(UPLOADER, request(LocalDate.now(KST), 1));

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<Photo>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(photoRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("gallery/a", captor.getValue().get(0).getObjectKey());
    }

    @Test
    void 사진_주소_변환은_객체_키마다_한_번씩_한다() {
        when(uploadConfirmService.confirmImages(anyLong(), any(UploadPurpose.class), anyList()))
                .thenReturn(List.of("gallery/a", "gallery/b"));
        PhotoUrlResolver resolver = mock(PhotoUrlResolver.class);
        when(resolver.resolve(anyString())).thenReturn("https://cdn.example/x");
        AlbumCommandService withMockResolver =
                new AlbumCommandService(
                        uploadConfirmService,
                        albumRepository,
                        photoRepository,
                        resolver,
                        objectStore);

        withMockResolver.create(UPLOADER, request(LocalDate.now(KST), 2));

        verify(resolver).resolve("gallery/a");
        verify(resolver).resolve("gallery/b");
    }

    // ---- 앨범 삭제 (#59) ----

    private static final long ALBUM_ID = 12L;
    private static final long OTHER_MEMBER = 8L;
    private static final long ADMIN = 1L;

    private Album givenAlbumWithPhotos(String... objectKeys) {
        Album album = Album.create("10월 정기 세미나", LocalDate.now(KST).minusDays(1), UPLOADER);
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.of(album));
        List<Photo> photos = new java.util.ArrayList<>();
        for (int order = 0; order < objectKeys.length; order++) {
            photos.add(Photo.create(ALBUM_ID, objectKeys[order], (short) order));
        }
        when(photoRepository.findByAlbumIdOrderByDisplayOrderAsc(ALBUM_ID)).thenReturn(photos);
        return album;
    }

    @Test
    void 작성자는_앨범과_사진_파일을_지운다() {
        Album album = givenAlbumWithPhotos("gallery/a", "gallery/b");

        service.delete(ALBUM_ID, new LoginUser(UPLOADER, Role.MEMBER));

        verify(albumRepository).delete(album);
        verify(objectStore).deleteAll(List.of("gallery/a", "gallery/b"));
    }

    @Test
    void 관리자는_다른_사람의_앨범을_지운다() {
        Album album = givenAlbumWithPhotos("gallery/a");

        service.delete(ALBUM_ID, new LoginUser(ADMIN, Role.ADMIN));

        verify(albumRepository).delete(album);
        verify(objectStore).deleteAll(List.of("gallery/a"));
    }

    @Test
    void 다른_동아리원은_지우지_못한다() {
        givenAlbumWithPhotos("gallery/a");

        BusinessException thrown =
                assertThrows(
                        BusinessException.class,
                        () -> service.delete(ALBUM_ID, new LoginUser(OTHER_MEMBER, Role.MEMBER)));

        assertEquals("GALLERY_FORBIDDEN", thrown.getErrorCode().code());
        verify(albumRepository, never()).delete(any(Album.class));
        verify(objectStore, never()).deleteAll(anyList());
    }

    @Test
    void 운영진도_관리자가_아니면_남의_앨범을_지우지_못한다() {
        givenAlbumWithPhotos("gallery/a");

        // 관리자는 ADMIN만이다 (PRODUCT.md 3절). STAFF는 운영진이라 포함하지 않는다.
        BusinessException thrown =
                assertThrows(
                        BusinessException.class,
                        () -> service.delete(ALBUM_ID, new LoginUser(OTHER_MEMBER, Role.STAFF)));

        assertEquals("GALLERY_FORBIDDEN", thrown.getErrorCode().code());
        verify(objectStore, never()).deleteAll(anyList());
    }

    @Test
    void 없는_앨범은_404다() {
        when(albumRepository.findById(ALBUM_ID)).thenReturn(Optional.empty());

        BusinessException thrown =
                assertThrows(
                        BusinessException.class,
                        () -> service.delete(ALBUM_ID, new LoginUser(UPLOADER, Role.MEMBER)));

        assertEquals("GALLERY_ALBUM_NOT_FOUND", thrown.getErrorCode().code());
        verify(objectStore, never()).deleteAll(anyList());
    }

    @Test
    void DB를_먼저_지우고_flush한_뒤_S3를_지운다() {
        Album album = givenAlbumWithPhotos("gallery/a");

        service.delete(ALBUM_ID, new LoginUser(UPLOADER, Role.MEMBER));

        // S3를 먼저 지우면 DB 삭제가 실패했을 때 사진만 사라진 앨범이 남는다 (5-2 5-2-2).
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(albumRepository, objectStore);
        inOrder.verify(albumRepository).delete(album);
        inOrder.verify(albumRepository).flush();
        inOrder.verify(objectStore).deleteAll(anyList());
    }

    @Test
    void S3_삭제가_실패하면_예외를_그대로_던져_롤백되게_한다() {
        givenAlbumWithPhotos("gallery/a");
        org.mockito.Mockito.doThrow(new IllegalStateException("S3 장애"))
                .when(objectStore)
                .deleteAll(anyList());

        // 예외를 삼키면 트랜잭션이 커밋되어 화면에서는 사라졌는데 사진 주소가 계속 열린다.
        assertThrows(
                IllegalStateException.class,
                () -> service.delete(ALBUM_ID, new LoginUser(UPLOADER, Role.MEMBER)));
    }

    @Test
    void 올린_직후_응답은_지울_수_있다고_준다() {
        when(uploadConfirmService.confirmImages(anyLong(), any(UploadPurpose.class), anyList()))
                .thenReturn(List.of("gallery/a"));

        AlbumDetailResponse response = service.create(UPLOADER, request(LocalDate.now(KST), 1));

        org.junit.jupiter.api.Assertions.assertTrue(response.canDelete());
    }
}
