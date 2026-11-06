package com.hackhu.hp.global.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 크기 제한이 S3 단계에서 강제되는지 확인한다 (docs/spec/gallery/backend/5-1 2절).
 *
 * <p>설계가 "신고한 size보다 큰 파일은 S3가 서명 불일치로 거부한다"에 걸려 있는데, 이는 SigV4의 SignedHeaders에 content-length가 실제로
 * 들어갔을 때만 성립한다. SDK가 {@code PutObjectRequest.contentLength()}를 서명 대상으로 올리지 않으면 신고보다 큰 파일도 그냥 통과한다.
 * 그래서 서명 파라미터를 직접 열어 확인한다.
 *
 * <p>서명 계산은 네트워크 없이 로컬에서 되므로 가짜 키로 검증할 수 있다.
 */
class PresignedUrlServiceTest {

    private static final String SIGNED_HEADERS_PARAM = "X-Amz-SignedHeaders=";

    private PresignedUrlService service() {
        S3Presigner presigner =
                S3Presigner.builder()
                        .region(Region.AP_NORTHEAST_2)
                        .credentialsProvider(
                                StaticCredentialsProvider.create(
                                        AwsBasicCredentials.create("AKIAEXAMPLE", "secretexample")))
                        .build();
        return new PresignedUrlService(presigner, "test-bucket");
    }

    /** X-Amz-SignedHeaders 파라미터를 세미콜론 기준으로 쪼갠다. */
    private static List<String> signedHeadersOf(URL url) {
        for (String pair : url.getQuery().split("&")) {
            if (pair.startsWith(SIGNED_HEADERS_PARAM)) {
                String value =
                        URLDecoder.decode(
                                pair.substring(SIGNED_HEADERS_PARAM.length()),
                                StandardCharsets.UTF_8);
                return Arrays.asList(value.split(";"));
            }
        }
        throw new AssertionError("X-Amz-SignedHeaders 파라미터가 없다: " + url);
    }

    @Test
    void 서명_대상_헤더에_content_length가_들어간다() {
        URL url =
                service()
                        .presignPut("gallery/abc", "image/jpeg", 1_048_576, Duration.ofMinutes(10));

        List<String> signedHeaders = signedHeadersOf(url);
        System.out.println("X-Amz-SignedHeaders = " + signedHeaders);

        assertTrue(
                signedHeaders.contains("content-length"),
                "content-length가 서명 대상이 아니면 신고보다 큰 파일도 업로드된다: " + signedHeaders);
    }

    @Test
    void 서명_대상_헤더에_content_type이_들어간다() {
        URL url =
                service()
                        .presignPut("gallery/abc", "image/jpeg", 1_048_576, Duration.ofMinutes(10));

        assertTrue(signedHeadersOf(url).contains("content-type"));
    }

    @Test
    void 크기가_다르면_서명_자체가_달라진다() {
        PresignedUrlService service = service();
        URL oneMb =
                service.presignPut("gallery/abc", "image/jpeg", 1_048_576, Duration.ofMinutes(10));
        URL twentyMb =
                service.presignPut("gallery/abc", "image/jpeg", 20_971_520, Duration.ofMinutes(10));

        String oneMbSignature = signatureOf(oneMb);
        String twentyMbSignature = signatureOf(twentyMb);
        System.out.println("1MB 서명  = " + oneMbSignature);
        System.out.println("20MB 서명 = " + twentyMbSignature);

        assertTrue(
                !oneMbSignature.equals(twentyMbSignature),
                "크기가 서명에 반영되지 않으면 같은 서명이 나온다. 그러면 1MB로 발급받아 20MB를 올릴 수 있다.");
    }

    private static String signatureOf(URL url) {
        for (String pair : url.getQuery().split("&")) {
            if (pair.startsWith("X-Amz-Signature=")) {
                return pair.substring("X-Amz-Signature=".length());
            }
        }
        throw new AssertionError("X-Amz-Signature 파라미터가 없다: " + url);
    }
}
