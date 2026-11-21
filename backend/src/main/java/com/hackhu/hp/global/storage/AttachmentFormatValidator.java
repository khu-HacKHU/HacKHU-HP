package com.hackhu.hp.global.storage;

import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 업로드된 첨부파일이 신고한 형식과 맞는지 매직바이트로 확인한다 (docs/spec/board/backend/5-1-QUALITY-SECURITY.md 5-1-7,
 * CWE-434).
 *
 * <p>pdf는 {@code %PDF-}, docx·pptx·xlsx는 zip 시작 {@code PK\x03\x04}만 본다. 세 오피스 형식은 안쪽이 zip이라 앞부분만으로는
 * 확장자만 바꾼 zip과 구분되지 않는다. zip 안의 구조까지 보지 않기로 했다 (board DECISIONS.md B9).
 */
@Service
public class AttachmentFormatValidator {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] ZIP = {'P', 'K', 0x03, 0x04};

    /** 허용하는 첨부 형식과 그 형식이 시작해야 하는 바이트. 발급 단계 허용 목록과 같다 (board 4-2-10). */
    static final Map<String, byte[]> SIGNATURES =
            Map.of(
                    "application/pdf", PDF,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ZIP,
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                            ZIP,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ZIP);

    private static final int HEADER_LENGTH = PDF.length;

    private final S3ObjectStore objectStore;

    public AttachmentFormatValidator(S3ObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    /** 발급 기록에 적힌 형식(클라이언트 값이 아니다)의 서명으로 시작하는지 본다. 객체가 없으면 거부한다. */
    public boolean matchesDeclaredType(UploadRecord record) {
        byte[] signature = SIGNATURES.get(record.getContentType());
        if (signature == null) {
            return false;
        }
        byte[] head = objectStore.readHead(record.getObjectKey(), HEADER_LENGTH);
        if (head.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (head[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
