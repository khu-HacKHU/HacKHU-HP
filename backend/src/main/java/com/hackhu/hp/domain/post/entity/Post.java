package com.hackhu.hp.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 게시판 글. 세 게시판이 같이 쓰고 {@link BoardType}으로 구분한다 (3-2-DESIGN-DB-SCHEMA.md 3-2-2, DECISIONS.md B1).
 */
@Entity
@Table(name = "post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, updatable = false, length = 20)
    private BoardType boardType;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 작성자 ({@code users.id}). 수정해도 바뀌지 않는다. 권한 검사는 이 값과 현재 사용자 ID를 비교한다 (5-1-3). */
    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    // 시각은 DB의 now()가 정한다. 서버 시계와 DB 시계가 섞이지 않게 저장 뒤 DB 값을 다시 읽는다.
    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // 행이 바뀔 때마다 트리거가 갱신한다. 코드에서 직접 쓰지 않는다 (backend/AGENTS.md, auth DECISIONS.md A19).
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    private Post(BoardType boardType, String title, String content, Long authorId) {
        this.boardType = boardType;
        this.title = title;
        this.content = content;
        this.authorId = authorId;
    }

    /**
     * 새 글.
     *
     * @param authorId 현재 사용자 ID. 요청 본문에서 받지 않는다 (5-1-3, CWE-915)
     * @param title 앞뒤 공백을 지우고 길이를 검사한 제목
     * @param content 줄바꿈을 {@code \n}으로 맞추고 길이를 검사한 본문
     */
    public static Post create(BoardType boardType, String title, String content, Long authorId) {
        return new Post(boardType, title, content, authorId);
    }
}
