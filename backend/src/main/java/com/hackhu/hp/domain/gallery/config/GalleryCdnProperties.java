package com.hackhu.hp.domain.gallery.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사진 조회 주소(CloudFront 서명 URL) 설정. 값은 application*.yml에서 환경변수로만 받는다.
 *
 * @param baseUrl CloudFront 배포 주소 (예: https://dxxxx.cloudfront.net)
 * @param keyPairId CloudFront 공개 키 ID
 * @param privateKey 서명용 RSA 개인 키 PEM(PKCS#8). 코드와 설정 파일에 적지 않는다 (secure-coding 6절)
 * @param urlTtl 서명 URL 만료 시간. 2-REQUIREMENTS 3절에 따라 1시간
 * @param allowEphemeralKey 개인 키가 없을 때 임시 키를 만들어 쓸지. local, 테스트 전용
 */
@ConfigurationProperties(prefix = "gallery.cdn")
public record GalleryCdnProperties(
        String baseUrl,
        String keyPairId,
        String privateKey,
        @DefaultValue("1h") Duration urlTtl,
        @DefaultValue("false") boolean allowEphemeralKey) {}
