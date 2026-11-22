package com.hackhu.hp.domain.post.entity;

/**
 * 글이 속한 게시판 (docs/spec/board/backend/1-BACKGROUND.md 1-3).
 *
 * <p>요청으로 받지 않는다. 게시판마다 있는 컨트롤러와 서비스가 정한다 (5-1-4).
 */
public enum BoardType {
    /** 공지. 누구나 보고 운영진이 쓴다. */
    NOTICE,
    /** 자유게시판. 동아리원만 보고 쓴다. */
    FREE,
    /** 자료게시판. 동아리원만 보고 쓰며 첨부파일이 붙는다 (#55). */
    ARCHIVE
}
