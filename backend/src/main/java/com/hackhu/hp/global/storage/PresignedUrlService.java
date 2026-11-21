package com.hackhu.hp.global.storage;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
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

    /**
     * 내려받기용 presigned GET URL을 만든다 (docs/spec/board/backend/5-1-QUALITY-SECURITY.md 5-1-9).
     *
     * <p>응답 헤더 덮어쓰기를 서명에 넣는다. {@code Content-Disposition}은 항상 {@code attachment}라 브라우저가 열거나 실행하지
     * 않고, {@code Content-Type}은 저장한 형식으로 고정한다. 클라이언트가 쿼리를 바꾸면 서명이 깨져 S3가 거부한다.
     *
     * @param fileName 내려받을 파일 이름. RFC 5987 형식으로 인코딩해 한글이 깨지지 않고 헤더에 특수 문자가 그대로 들어가지 않는다 (CWE-113)
     */
    public URL presignGet(String objectKey, String contentType, String fileName, Duration expiry) {
        GetObjectRequest objectRequest =
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .responseContentType(contentType)
                        .responseContentDisposition(contentDisposition(fileName))
                        .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(expiry)
                        .getObjectRequest(objectRequest)
                        .build();

        return presigner.presignGetObject(presignRequest).url();
    }

    /** {@code attachment; filename*=UTF-8''...}. RFC 5987의 attr-char 밖은 모두 퍼센트 인코딩한다. */
    static String contentDisposition(String fileName) {
        String encoded =
                URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                        .replace("+", "%20")
                        .replace("*", "%2A")
                        .replace("%7E", "~");
        return "attachment; filename*=UTF-8''" + encoded;
    }
}
