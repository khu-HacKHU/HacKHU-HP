package com.hackhu.hp.domain.post.entity;

/**
 * 글이 속한 게시판 (docs/spec/board/backend/1-BACKGROUND.md 1-3).
 *
 * <p>요청으로 받지 않는다. 컨트롤러가 정해서 서비스에 넘긴다 (5-1-4). 자유게시판과 자료게시판은 #31에서 더한다.
 */
public enum BoardType {
    NOTICE
}
