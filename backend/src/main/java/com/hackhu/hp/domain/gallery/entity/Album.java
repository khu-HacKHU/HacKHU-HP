package com.hackhu.hp.domain.gallery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 제목, 활동 날짜, 사진 1~20장을 묶은 갤러리 게시 단위. 수정하지 않는다 (D9). */
@Entity
@Table(name = "album")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /** 올린 사람. 응답에 노출하지 않고 삭제 권한 검사(#59)에만 쓴다. */
    @Column(name = "uploader_id", nullable = false, updatable = false)
    private Long uploaderId;

    /** TIMESTAMPTZ 컬럼. 시각은 Instant(UTC 기준 한 시점)로만 다룬다. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Album(String title, LocalDate eventDate, Long uploaderId) {
        this.title = title;
        this.eventDate = eventDate;
        this.uploaderId = uploaderId;
        this.createdAt = Instant.now();
    }

    /** 입력 검증(제목 길이, 미래 날짜 금지)은 호출하는 쪽이 먼저 한다 (5-1 2절). */
    public static Album create(String title, LocalDate eventDate, Long uploaderId) {
        return new Album(title, eventDate, uploaderId);
    }
}
