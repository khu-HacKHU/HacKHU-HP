package com.hackhu.hp.domain.gallery.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackhu.hp.domain.gallery.dto.UploadUrlRequest;
import com.hackhu.hp.domain.gallery.dto.UploadUrlResponse;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.storage.PresignedUrlService;
import com.hackhu.hp.global.storage.UploadRecord;
import com.hackhu.hp.global.storage.UploadRecordRepository;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GalleryUploadServiceTest {

    private static final long TEN_MB = 10L * 1024 * 1024;
    private static final Long USER_ID = 7L;

    private PresignedUrlService presignedUrlService;
    private UploadRecordRepository uploadRecordRepository;
    private GalleryUploadService service;

    @BeforeEach
    void setUp() throws Exception {
        presignedUrlService = mock(PresignedUrlService.class);
        uploadRecordRepository = mock(UploadRecordRepository.class);
        service =
                new GalleryUploadService(
                        presignedUrlService, uploadRecordRepository, new UploadRateLimiter());

        URL url = URI.create("https://example.com/presigned").toURL();
        when(presignedUrlService.presignPut(
                        anyString(), anyString(), anyLong(), any(Duration.class)))
                .thenReturn(url);
        when(uploadRecordRepository.save(any(UploadRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 요청한_파일_수만큼_업로드_주소를_발급한다() {
        UploadUrlRequest request =
                new UploadUrlRequest(
                        List.of(
                                new UploadUrlRequest.FileSpec("image/jpeg", 1024),
                                new UploadUrlRequest.FileSpec("image/webp", 2048)));

        UploadUrlResponse response = service.issueUploadUrls(USER_ID, request);

        assertEquals(2, response.uploads().size());
        response.uploads().forEach(upload -> assertNotNull(upload.uploadId()));
        verify(uploadRecordRepository, times(2)).save(any(UploadRecord.class));
    }

    @Test
    void 허용하지_않는_형식은_거부한다() {
        UploadUrlRequest request =
                new UploadUrlRequest(List.of(new UploadUrlRequest.FileSpec("image/svg+xml", 1024)));

        BusinessException thrown =
                assertThrows(
                        BusinessException.class, () -> service.issueUploadUrls(USER_ID, request));

        assertEquals("GALLERY_UNSUPPORTED_FORMAT", thrown.getErrorCode().code());
        verify(uploadRecordRepository, never()).save(any(UploadRecord.class));
    }

    @Test
    void 십메가바이트를_넘는_크기는_발급_전에_거부한다() {
        UploadUrlRequest request =
                new UploadUrlRequest(
                        List.of(new UploadUrlRequest.FileSpec("image/jpeg", TEN_MB + 1)));

        BusinessException thrown =
                assertThrows(
                        BusinessException.class, () -> service.issueUploadUrls(USER_ID, request));

        assertEquals("GALLERY_FILE_TOO_LARGE", thrown.getErrorCode().code());
        verify(presignedUrlService, never())
                .presignPut(anyString(), anyString(), anyLong(), any(Duration.class));
    }

    @Test
    void 분당_호출_한도를_넘으면_거부한다() {
        UploadUrlRequest request =
                new UploadUrlRequest(List.of(new UploadUrlRequest.FileSpec("image/jpeg", 1024)));

        for (int i = 0; i < 5; i++) {
            service.issueUploadUrls(USER_ID, request);
        }

        BusinessException thrown =
                assertThrows(
                        BusinessException.class, () -> service.issueUploadUrls(USER_ID, request));

        assertEquals("GALLERY_TOO_MANY_REQUESTS", thrown.getErrorCode().code());
    }
}
