package com.hackhu.hp.global.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * presigned 업로드 주소를 발급할 때마다 남기는 기록. 갤러리 앨범 사진과 자료게시판 첨부파일이 같이 쓴다 (docs/spec/gallery/backend/3-2
 * 4절).
 *
 * <p>확정 API는 클라이언트가 보낸 {@code uploadId}가 이 기록에 있고, 호출자 본인에게 발급됐고, 아직 안 쓰였는지 확인한 뒤에만 실제 object_key를
 * 꺼내 쓴다. 클라이언트가 S3 객체 키를 직접 다루지 않게 하려는 것이다.
 */
@Entity
@Table(name = "upload_record")
public class UploadRecord {

    @Id private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UploadPurpose purpose;

    @Column(name = "object_key", nullable = false, unique = true, length = 255)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UploadStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UploadRecord() {}

    private UploadRecord(
            UUID id,
            Long userId,
            UploadPurpose purpose,
            String objectKey,
            String contentType,
            long sizeBytes,
            Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.purpose = purpose;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = UploadStatus.ISSUED;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    /**
     * 새 발급 기록을 만든다. object_key는 {purpose}/{uuid} 형식으로 서버가 생성한다.
     *
     * <p>ponytail: 확장자는 키에 안 붙인다. 실제 파일 형식은 매직바이트 검증으로 확정되는데, 검증 전에 키를 먼저 발급해야 해서 확장자를 미리 넣을 수가 없다.
     * S3 서빙은 Content-Type 메타데이터로 되니 키의 확장자는 꼭 필요하지 않다. 사람이 S3 콘솔에서 보기 편하게 확장자를 넣고 싶어지면, 검증 통과 후
     * CopyObject로 옮기는 단계를 추가한다.
     */
    public static UploadRecord issue(
            Long userId,
            UploadPurpose purpose,
            String contentType,
            long sizeBytes,
            Instant expiresAt) {
        UUID id = UUID.randomUUID();
        String objectKey = purpose.name().toLowerCase() + "/" + id;
        return new UploadRecord(id, userId, purpose, objectKey, contentType, sizeBytes, expiresAt);
    }

    public void markUsed() {
        this.status = UploadStatus.USED;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isUsable(Instant now) {
        return status == UploadStatus.ISSUED && now.isBefore(expiresAt);
    }

    /** 아직 어느 앨범·게시글에도 붙지 않은 상태. 이 객체는 지워도 살아있는 콘텐츠를 깨지 않는다. */
    public boolean isPending() {
        return status == UploadStatus.ISSUED;
    }

    public UploadPurpose getPurpose() {
        return purpose;
    }

    public UUID getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }
}
