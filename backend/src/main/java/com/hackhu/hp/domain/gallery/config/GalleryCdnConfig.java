package com.hackhu.hp.domain.gallery.config;

import com.hackhu.hp.domain.gallery.service.CloudFrontPhotoUrlResolver;
import com.hackhu.hp.domain.gallery.service.PhotoUrlResolver;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GalleryCdnProperties.class)
class GalleryCdnConfig {

    @Bean
    PhotoUrlResolver photoUrlResolver(GalleryCdnProperties properties) {
        // 설정이 빠졌으면 서버를 띄우지 않는다. 깨진 사진 주소를 내려주는 것보다 배포 단계에서 바로 드러나는 편이 낫다.
        if (!StringUtils.hasText(properties.baseUrl())
                || !StringUtils.hasText(properties.keyPairId())) {
            throw new IllegalStateException(
                    "gallery.cdn.base-url과 gallery.cdn.key-pair-id를 설정해야 합니다.");
        }
        if (!properties.baseUrl().startsWith("https://")) {
            throw new IllegalStateException("gallery.cdn.base-url은 https 주소여야 합니다.");
        }
        return new CloudFrontPhotoUrlResolver(
                properties.baseUrl(),
                properties.keyPairId(),
                privateKey(properties),
                properties.urlTtl(),
                Clock.systemUTC());
    }

    private static PrivateKey privateKey(GalleryCdnProperties properties) {
        if (StringUtils.hasText(properties.privateKey())) {
            return PemPrivateKeys.parseRsa(properties.privateKey());
        }
        if (!properties.allowEphemeralKey()) {
            throw new IllegalStateException("gallery.cdn.private-key를 설정해야 합니다.");
        }
        // local과 테스트에서만 쓴다. 이 키로 만든 URL은 실제 CloudFront에서 열리지 않는다.
        log.warn("gallery.cdn.private-key가 없어 임시 키로 서명합니다. 사진 주소는 실제로 열리지 않습니다.");
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair().getPrivate();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("임시 RSA 키를 만들 수 없습니다.", e);
        }
    }
}
