package com.hackhu.hp.domain.gallery.entity;

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

/** 앨범에 속한 사진 한 장. 파일 바이트는 S3에 있고 여기는 객체 키만 둔다. */
@Entity
@Table(name = "photo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Photo {

    /** 대표 사진의 순서. 앨범의 첫 번째 사진이 대표 사진이다. */
    public static final short COVER_ORDER = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 앨범이 지워지면 DB의 ON DELETE CASCADE로 같이 지워진다 (3-2 3절). */
    @Column(name = "album_id", nullable = false, updatable = false)
    private Long albumId;

    /** 서버가 만든 S3 객체 키. 응답에는 키 대신 서명 URL만 내려준다. */
    @Column(name = "object_key", nullable = false, unique = true, updatable = false)
    private String objectKey;

    @Column(name = "display_order", nullable = false, updatable = false)
    private short displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Photo(Long albumId, String objectKey, short displayOrder) {
        this.albumId = albumId;
        this.objectKey = objectKey;
        this.displayOrder = displayOrder;
        this.createdAt = Instant.now();
    }

    public static Photo create(Long albumId, String objectKey, short displayOrder) {
        return new Photo(albumId, objectKey, displayOrder);
    }
}
