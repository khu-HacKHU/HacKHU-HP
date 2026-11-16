package com.hackhu.hp.global.storage;

import org.springframework.stereotype.Service;

/**
 * 업로드된 파일이 실제로 jpg, png, webp인지 매직바이트로 확인한다 (docs/security/secure-coding.md 5절, CWE-434).
 *
 * <p>presigned 서명에 Content-Type을 고정해도 그건 클라이언트가 보낸 헤더 문자열일 뿐이라, 실제 바이트가 이미지인지는 올라온 뒤에 직접 봐야 한다. 허용
 * 목록 방식이라 SVG나 HTML처럼 서명이 없는 텍스트 파일은 자동으로 걸러진다.
 */
@Service
public class ImageFormatValidator {

    /** WebP는 8~11바이트의 "WEBP"까지 봐야 해서 셋 중 가장 길다. */
    private static final int HEADER_LENGTH = 12;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
    };
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    private final S3ObjectStore objectStore;

    public ImageFormatValidator(S3ObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    public boolean isSupportedImage(String objectKey) {
        byte[] head = objectStore.readHead(objectKey, HEADER_LENGTH);
        return matches(head, JPEG, 0)
                || matches(head, PNG, 0)
                || (matches(head, RIFF, 0) && matches(head, WEBP, 8));
    }

    private static boolean matches(byte[] head, byte[] signature, int offset) {
        if (head.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (head[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
