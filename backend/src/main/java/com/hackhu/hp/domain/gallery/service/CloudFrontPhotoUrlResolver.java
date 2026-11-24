package com.hackhu.hp.domain.gallery.service;

import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import software.amazon.awssdk.services.cloudfront.CloudFrontUtilities;
import software.amazon.awssdk.services.cloudfront.model.CannedSignerRequest;

/**
 * CloudFront 서명 URL(canned policy)을 만든다.
 *
 * <p>갤러리는 공개 콘텐츠지만 "파일은 presigned URL로만 주고받는다"는 루트 AGENTS.md 규칙 때문에 조회에도 서명을 건다 (1-BACKGROUND 3절).
 * 서명은 개인 키로 로컬에서 계산하므로 외부 호출이 없다.
 */
public class CloudFrontPhotoUrlResolver implements PhotoUrlResolver {

    private final CloudFrontUtilities utilities = CloudFrontUtilities.create();
    private final String baseUrl;
    private final String keyPairId;
    private final PrivateKey privateKey;
    private final Duration urlTtl;
    private final Clock clock;

    public CloudFrontPhotoUrlResolver(
            String baseUrl, String keyPairId, PrivateKey privateKey, Duration urlTtl, Clock clock) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.keyPairId = keyPairId;
        this.privateKey = privateKey;
        this.urlTtl = urlTtl;
        this.clock = clock;
    }

    @Override
    public String resolve(String objectKey) {
        // 객체 키는 서버가 만든 `{용도}/{uuid}.{확장자}` 형식이라(3-2 2절) 경로에 그대로 붙여도 안전하다.
        // 사용자 입력이 키에 들어가지 않으므로 경로 조작(CWE-22) 여지가 없다.
        Instant expiresAt = Instant.now(clock).plus(urlTtl);
        CannedSignerRequest request =
                CannedSignerRequest.builder()
                        .resourceUrl(baseUrl + "/" + objectKey)
                        .privateKey(privateKey)
                        .keyPairId(keyPairId)
                        .expirationDate(expiresAt)
                        .build();
        return utilities.getSignedUrlWithCannedPolicy(request).url();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
