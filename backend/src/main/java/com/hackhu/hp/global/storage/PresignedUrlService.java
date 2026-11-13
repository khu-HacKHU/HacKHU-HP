package com.hackhu.hp.global.storage;

import java.net.URL;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * presigned PUT URL을 만든다. contentType과 size를 요청에 같이 서명해서, 실제 업로드가 그 값과 정확히 다르면 S3가 서명 불일치로 거부하게 한다
 * (docs/spec/gallery/backend/5-1 2절).
 */
@Service
public class PresignedUrlService {

    private final S3Presigner presigner;
    private final String bucket;

    public PresignedUrlService(
            S3Presigner presigner, @Value("${storage.s3.bucket}") String bucket) {
        this.presigner = presigner;
        this.bucket = bucket;
    }

    public URL presignPut(String objectKey, String contentType, long sizeBytes, Duration expiry) {
        PutObjectRequest objectRequest =
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .contentLength(sizeBytes)
                        .build();

        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(expiry)
                        .putObjectRequest(objectRequest)
                        .build();

        PresignedPutObjectRequest presigned = presigner.presignPutObject(presignRequest);
        return presigned.url();
    }
}
