package com.hackhu.hp.global.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;

class UploadConfirmServiceTest {

    private static final Long OWNER = 7L;
    private static final Long OTHER_USER = 99L;

    private UploadRecordRepository repository;
    private ImageFormatValidator imageFormatValidator;
    private S3ObjectStore objectStore;
    private UploadConfirmService service;

    @BeforeEach
    void setUp() {
        repository = mock(UploadRecordRepository.class);
        imageFormatValidator = mock(ImageFormatValidator.class);
        objectStore = mock(S3ObjectStore.class);
        service = new UploadConfirmService(repository, imageFormatValidator, objectStore);

        when(imageFormatValidator.isSupportedImage(anyString())).thenReturn(true);
    }

    private UploadRecord issued(Long userId) {
        return UploadRecord.issue(
                userId,
                UploadPurpose.GALLERY,
                "image/jpeg",
                1024,
                Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    private void givenRecords(UploadRecord... records) {
        when(repository.findAllById(anyList())).thenReturn(List.of(records));
    }

    @Test
    void 전부_통과하면_요청_순서대로_키를_돌려주고_소비_처리한다() {
        UploadRecord first = issued(OWNER);
        UploadRecord second = issued(OWNER);
        givenRecords(first, second);

        List<String> keys =
                service.confirmImages(
                        OWNER, UploadPurpose.GALLERY, List.of(first.getId(), second.getId()));

        assertEquals(List.of(first.getObjectKey(), second.getObjectKey()), keys);
        assertFalse(first.isPending());
        assertFalse(second.isPending());
        verify(objectStore, never()).deleteAll(anyList());
    }

    @Test
    void 남이_발급받은_업로드는_거부한다() {
        UploadRecord mine = issued(OWNER);
        UploadRecord stolen = issued(OTHER_USER);
        givenRecords(mine, stolen);

        UploadConfirmException thrown =
                assertThrows(
                        UploadConfirmException.class,
                        () ->
                                service.confirmImages(
                                        OWNER,
                                        UploadPurpose.GALLERY,
                                        List.of(mine.getId(), stolen.getId())));

        assertEquals(1, thrown.getFailures().size());
        assertEquals(1, thrown.getFailures().get(0).index());
        // 남의 객체는 지우지 않고, 내 객체만 정리한다.
        verify(objectStore).deleteAll(List.of(mine.getObjectKey()));
        assertTrue(stolen.isPending());
    }

    @Test
    void 발급_기록에_없는_업로드는_거부한다() {
        givenRecords();
        UUID unknown = UUID.randomUUID();

        UploadConfirmException thrown =
                assertThrows(
                        UploadConfirmException.class,
                        () ->
                                service.confirmImages(
                                        OWNER, UploadPurpose.GALLERY, List.of(unknown)));

        assertEquals(1, thrown.getFailures().size());
    }

    @Test
    void 만료된_업로드는_거부한다() {
        UploadRecord expired =
                UploadRecord.issue(
                        OWNER,
                        UploadPurpose.GALLERY,
                        "image/jpeg",
                        1024,
                        Instant.now().minus(1, ChronoUnit.MINUTES));
        givenRecords(expired);

        UploadConfirmException thrown =
                assertThrows(
                        UploadConfirmException.class,
                        () ->
                                service.confirmImages(
                                        OWNER, UploadPurpose.GALLERY, List.of(expired.getId())));

        assertEquals("이미 사용했거나 만료된 업로드 주소입니다.", thrown.getFailures().get(0).reason());
    }

    @Test
    void 이미_확정에_쓴_업로드는_거부하고_그_객체는_지우지_않는다() {
        UploadRecord used = issued(OWNER);
        used.markUsed();
        givenRecords(used);

        assertThrows(
                UploadConfirmException.class,
                () -> service.confirmImages(OWNER, UploadPurpose.GALLERY, List.of(used.getId())));

        verify(objectStore).deleteAll(List.of());
    }

    @Test
    void 매직바이트가_맞지_않으면_전체를_거부하고_올라온_객체를_지운다() {
        UploadRecord good = issued(OWNER);
        UploadRecord bad = issued(OWNER);
        givenRecords(good, bad);
        when(imageFormatValidator.isSupportedImage(bad.getObjectKey())).thenReturn(false);

        UploadConfirmException thrown =
                assertThrows(
                        UploadConfirmException.class,
                        () ->
                                service.confirmImages(
                                        OWNER,
                                        UploadPurpose.GALLERY,
                                        List.of(good.getId(), bad.getId())));

        assertEquals("실제 파일이 jpg, png, webp 형식이 아닙니다.", thrown.getFailures().get(0).reason());
        // 통과한 사진까지 같이 지운다. 앨범이 안 만들어지면 남겨둘 이유가 없다.
        verify(objectStore).deleteAll(List.of(good.getObjectKey(), bad.getObjectKey()));
    }

    @Test
    void 같은_업로드를_두_번_넣으면_거부한다() {
        UploadRecord record = issued(OWNER);
        givenRecords(record);

        UploadConfirmException thrown =
                assertThrows(
                        UploadConfirmException.class,
                        () ->
                                service.confirmImages(
                                        OWNER,
                                        UploadPurpose.GALLERY,
                                        List.of(record.getId(), record.getId())));

        assertEquals("같은 사진을 두 번 넣을 수 없습니다.", thrown.getFailures().get(0).reason());
    }

    @Test
    void 용도가_다른_업로드는_거부한다() {
        UploadRecord attachment =
                UploadRecord.issue(
                        OWNER,
                        UploadPurpose.ATTACHMENT,
                        "image/jpeg",
                        1024,
                        Instant.now().plus(10, ChronoUnit.MINUTES));
        givenRecords(attachment);

        assertThrows(
                UploadConfirmException.class,
                () ->
                        service.confirmImages(
                                OWNER, UploadPurpose.GALLERY, List.of(attachment.getId())));
    }

    @Test
    void S3_장애로_검사를_못_하면_기록을_소비하지_않고_그대로_터뜨린다() {
        UploadRecord record = issued(OWNER);
        givenRecords(record);
        when(imageFormatValidator.isSupportedImage(record.getObjectKey()))
                .thenThrow(SdkClientException.create("connection reset"));

        // 클라이언트 잘못이 아니라 우리 쪽 문제라, 확정 실패(400)로 바꾸지 않고 그대로 올려 500이 되게 한다.
        assertThrows(
                SdkClientException.class,
                () -> service.confirmImages(OWNER, UploadPurpose.GALLERY, List.of(record.getId())));

        // 기록이 ISSUED로 남아야 사용자가 사진을 다시 올리지 않고 재시도할 수 있다.
        assertTrue(record.isPending());
        verify(objectStore, never()).deleteAll(anyList());
    }

    @Test
    void 매직바이트_검사는_소유자_확인을_통과한_것만_한다() {
        UploadRecord stolen = issued(OTHER_USER);
        givenRecords(stolen);

        assertThrows(
                UploadConfirmException.class,
                () -> service.confirmImages(OWNER, UploadPurpose.GALLERY, List.of(stolen.getId())));

        // 비싼 S3 호출을 아끼는 순서인지 확인한다.
        verify(imageFormatValidator, never()).isSupportedImage(any());
    }
}
