package com.hackhu.hp.domain.post.entity;

import com.hackhu.hp.global.storage.UploadRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;

/**
 * 자료게시판 글의 첨부파일 하나 (3-2-DESIGN-DB-SCHEMA.md 3-2-3).
 *
 * <p>글과 연관관계를 맺지 않고 {@code postId}만 가진다. 글 삭제는 DB의 {@code ON DELETE CASCADE}가 지운다.
 */
@Entity
@Table(name = "attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment {

    /** 글 하나에 붙일 수 있는 최대 개수 (2-REQUIREMENTS.md, 4-2-9). */
    public static final int MAX_PER_POST = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;

    /** S3 객체 키. 응답에 넣지 않는다 (4-2-9). */
    @Column(name = "object_key", nullable = false, updatable = false, length = 255)
    private String objectKey;

    @Column(name = "file_name", nullable = false, updatable = false, length = 200)
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    private Attachment(
            Long postId,
            String objectKey,
            String fileName,
            String contentType,
            long sizeBytes,
            int displayOrder) {
        this.postId = postId;
        this.objectKey = objectKey;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.displayOrder = (short) displayOrder;
    }

    /** 확정한 업로드로 첨부를 만든다. 형식, 크기, 파일 이름은 클라이언트가 아니라 발급 기록의 값을 쓴다 (5-1-7 "발급과 확정의 연결"). */
    public static Attachment from(Long postId, UploadRecord upload, int displayOrder) {
        return new Attachment(
                postId,
                upload.getObjectKey(),
                upload.getOriginalFileName(),
                upload.getContentType(),
                upload.getSizeBytes(),
                displayOrder);
    }

    /** 수정에서 남은 첨부의 순서를 다시 매긴다 (3-2-4). */
    public void reorder(int displayOrder) {
        this.displayOrder = (short) displayOrder;
    }
}
