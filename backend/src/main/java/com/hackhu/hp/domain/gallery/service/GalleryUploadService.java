package com.hackhu.hp.domain.gallery.service;

import com.hackhu.hp.domain.gallery.dto.UploadUrlRequest;
import com.hackhu.hp.domain.gallery.dto.UploadUrlResponse;
import com.hackhu.hp.domain.gallery.exception.GalleryErrorCode;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.storage.PresignedUrlService;
import com.hackhu.hp.global.storage.UploadPurpose;
import com.hackhu.hp.global.storage.UploadRecord;
import com.hackhu.hp.global.storage.UploadRecordRepository;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 업로드 주소 발급 (docs/spec/gallery/backend/4-2 3절, 5-1 2절). */
@Service
public class GalleryUploadService {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    private static final Duration UPLOAD_EXPIRY = Duration.ofMinutes(10);

    private final PresignedUrlService presignedUrlService;
    private final UploadRecordRepository uploadRecordRepository;
    private final UploadRateLimiter rateLimiter;

    public GalleryUploadService(
            PresignedUrlService presignedUrlService,
            UploadRecordRepository uploadRecordRepository,
            UploadRateLimiter rateLimiter) {
        this.presignedUrlService = presignedUrlService;
        this.uploadRecordRepository = uploadRecordRepository;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public UploadUrlResponse issueUploadUrls(Long userId, UploadUrlRequest request) {
        rateLimiter.checkAndRecord(userId, request.files().size());

        Instant expiresAt = Instant.now().plus(UPLOAD_EXPIRY);
        List<UploadUrlResponse.Upload> uploads = new ArrayList<>();

        for (UploadUrlRequest.FileSpec file : request.files()) {
            validate(file);

            UploadRecord record =
                    UploadRecord.issue(
                            userId,
                            UploadPurpose.GALLERY,
                            file.contentType(),
                            file.size(),
                            expiresAt);
            uploadRecordRepository.save(record);

            URL uploadUrl =
                    presignedUrlService.presignPut(
                            record.getObjectKey(), file.contentType(), file.size(), UPLOAD_EXPIRY);
            uploads.add(new UploadUrlResponse.Upload(record.getId(), uploadUrl, expiresAt));
        }

        return new UploadUrlResponse(uploads);
    }

    private void validate(UploadUrlRequest.FileSpec file) {
        if (!ALLOWED_CONTENT_TYPES.contains(file.contentType())) {
            throw new BusinessException(GalleryErrorCode.UNSUPPORTED_FORMAT);
        }
        // 크기는 여기서 거른 뒤 presigned 서명에 그대로 들어간다. 그래서 신고보다 큰 파일은 S3가 업로드 단계에서 거부한다.
        if (file.size() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(GalleryErrorCode.FILE_TOO_LARGE);
        }
    }
}
