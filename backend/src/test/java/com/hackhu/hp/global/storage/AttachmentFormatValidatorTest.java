package com.hackhu.hp.global.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttachmentFormatValidatorTest {

    private static final String PDF = "application/pdf";
    private static final String DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private S3ObjectStore objectStore;
    private AttachmentFormatValidator validator;

    @BeforeEach
    void setUp() {
        objectStore = mock(S3ObjectStore.class);
        validator = new AttachmentFormatValidator(objectStore);
    }

    private void givenHead(byte[] head) {
        when(objectStore.readHead(anyString(), anyInt())).thenReturn(head);
    }

    private static UploadRecord declared(String contentType) {
        return UploadRecord.issue(
                1L, UploadPurpose.ATTACHMENT, contentType, 100, Instant.now(), "a.pdf");
    }

    @Test
    void pdf로_신고하고_PDF로_시작하면_통과한다() {
        givenHead("%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        assertTrue(validator.matchesDeclaredType(declared(PDF)));
    }

    @Test
    void 오피스_형식으로_신고하고_zip으로_시작하면_통과한다() {
        givenHead(new byte[] {'P', 'K', 3, 4, 20});
        assertTrue(validator.matchesDeclaredType(declared(DOCX)));
    }

    @Test
    void 신고한_형식과_다른_서명이면_거부한다() {
        // zip을 pdf로, pdf를 docx로 신고한 경우 모두 거부한다.
        givenHead(new byte[] {'P', 'K', 3, 4, 20});
        assertFalse(validator.matchesDeclaredType(declared(PDF)));

        givenHead("%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        assertFalse(validator.matchesDeclaredType(declared(DOCX)));
    }

    @Test
    void 올리지_않았거나_짧은_파일은_거부한다() {
        givenHead(new byte[0]);
        assertFalse(validator.matchesDeclaredType(declared(PDF)));

        givenHead(new byte[] {'%', 'P'});
        assertFalse(validator.matchesDeclaredType(declared(PDF)));
    }

    @Test
    void 허용하지_않는_형식은_S3를_읽지_않고_거부한다() {
        assertFalse(validator.matchesDeclaredType(declared("text/html")));
    }
}
