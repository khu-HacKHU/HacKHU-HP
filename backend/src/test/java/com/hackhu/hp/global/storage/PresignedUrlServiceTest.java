package com.hackhu.hp.global.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 크기 제한이 S3 단계에서 강제되는지 확인한다. 서명에 content-length가 안 들어가면, 신고한 크기보다 큰 파일을 올려도 S3가 통과시켜 버린다
 * (docs/spec/gallery/backend/5-1 2절). 서명 계산은 네트워크 없이 로컬에서 되므로 가짜 키로 검증할 수 있다.
 */
class PresignedUrlServiceTest {

    @Test
    void 서명에_contentType과_contentLength가_포함된다() {
        S3Presigner presigner =
                S3Presigner.builder()
                        .region(Region.AP_NORTHEAST_2)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("test-key", "test-secret")))
                        .build();
        PresignedUrlService service = new PresignedUrlService(presigner, "test-bucket");

        URL url =
                service.presignPut(
                        "gallery/11111111-1111-1111-1111-111111111111",
                        "image/jpeg",
                        1024,
                        Duration.ofMinutes(10));

        String query = url.getQuery();
        assertTrue(query.contains("content-length"), "서명 헤더에 content-length가 없다: " + query);
        assertTrue(query.contains("content-type"), "서명 헤더에 content-type이 없다: " + query);
    }
}
