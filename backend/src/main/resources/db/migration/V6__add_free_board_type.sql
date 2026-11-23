-- 자유게시판(FREE) 글을 받는다 (docs/spec/board/backend/3-2-DESIGN-DB-SCHEMA.md 3-2-2, #53).
-- 자료게시판(ARCHIVE)은 첨부파일 테이블과 함께 #55에서 더한다.
ALTER TABLE post DROP CONSTRAINT ck_post_board_type;
ALTER TABLE post ADD CONSTRAINT ck_post_board_type CHECK (board_type IN ('NOTICE', 'FREE'));
