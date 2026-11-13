package com.hackhu.hp.global.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImageFormatValidatorTest {

    private S3ObjectStore objectStore;
    private ImageFormatValidator validator;

    @BeforeEach
    void setUp() {
        objectStore = mock(S3ObjectStore.class);
        validator = new ImageFormatValidator(objectStore);
    }

    private void givenHead(byte[] head) {
        when(objectStore.readHead(anyString(), anyInt())).thenReturn(head);
    }

    private void givenHead(String text) {
        givenHead(text.getBytes(StandardCharsets.US_ASCII));
    }

    /** RIFF 컨테이너. 4~7바이트는 파일 크기 자리라 검사하지 않으므로 아무 값이나 넣는다. */
    private static byte[] riffHead(String fourCc) {
        byte[] head = new byte[12];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, head, 0, 4);
        System.arraycopy(fourCc.getBytes(StandardCharsets.US_ASCII), 0, head, 8, 4);
        return head;
    }

    @Test
    void jpeg_서명을_통과시킨다() {
        givenHead(
                new byte[] {
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0, 0, 0, 0, 0
                });

        assertTrue(validator.isSupportedImage("gallery/a"));
    }

    @Test
    void png_서명을_통과시킨다() {
        givenHead(
                new byte[] {
                    (byte) 0x89,
                    'P',
                    'N',
                    'G',
                    (byte) 0x0D,
                    (byte) 0x0A,
                    (byte) 0x1A,
                    (byte) 0x0A,
                    0,
                    0,
                    0,
                    0
                });

        assertTrue(validator.isSupportedImage("gallery/a"));
    }

    @Test
    void webp_서명을_통과시킨다() {
        givenHead(riffHead("WEBP"));

        assertTrue(validator.isSupportedImage("gallery/a"));
    }

    @Test
    void 앞부분이_RIFF여도_WEBP가_아니면_걸러진다() {
        givenHead(riffHead("WAVE"));

        assertFalse(validator.isSupportedImage("gallery/a"));
    }

    @Test
    void svg는_서명이_없어서_걸러진다() {
        givenHead("<svg xmlns=\"");

        assertFalse(validator.isSupportedImage("gallery/a"));
    }

    @Test
    void html도_걸러진다() {
        givenHead("<!DOCTYPE ht");

        assertFalse(validator.isSupportedImage("gallery/a"));
    }

    @Test
    void 업로드하지_않아_객체가_없으면_걸러진다() {
        givenHead(new byte[0]);

        assertFalse(validator.isSupportedImage("gallery/a"));
    }

    @Test
    void jpeg_서명이_잘려_있으면_걸러진다() {
        givenHead(new byte[] {(byte) 0xFF, (byte) 0xD8});

        assertFalse(validator.isSupportedImage("gallery/a"));
    }
}
