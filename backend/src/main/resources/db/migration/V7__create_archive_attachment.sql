-- 자료게시판(ARCHIVE) 글과 첨부파일 (docs/spec/board/backend/3-2-DESIGN-DB-SCHEMA.md 3-2-2, 3-2-3, #55).

ALTER TABLE post DROP CONSTRAINT ck_post_board_type;
ALTER TABLE post ADD CONSTRAINT ck_post_board_type
    CHECK (board_type IN ('NOTICE', 'FREE', 'ARCHIVE'));

-- global/storage 발급 기록을 첨부파일까지 받게 넓힌다.
-- docx, pptx, xlsx의 MIME 타입은 71~73자라 50자에 들어가지 않는다.
ALTER TABLE upload_record ALTER COLUMN content_type TYPE VARCHAR(100);
-- 첨부는 원래 파일 이름을 발급할 때 받아 두고 확정할 때 옮겨 적는다 (board 5-1-7). 갤러리는 쓰지 않아 NULL이다.
ALTER TABLE upload_record ADD COLUMN original_file_name VARCHAR(200);

CREATE TABLE attachment (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id BIGINT NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    object_key VARCHAR(255) NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    display_order SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_attachment_object_key UNIQUE (object_key),
    -- 수정에서 남는 첨부의 순서를 다시 매길 때 행마다 갱신하는 도중에 순서가 잠깐 겹친다. 검사를 커밋 시점으로 미룬다.
    CONSTRAINT uq_attachment_post_display_order UNIQUE (post_id, display_order)
        DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_attachment_content_type CHECK (content_type IN (
        'application/pdf',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')),
    CONSTRAINT ck_attachment_size CHECK (size_bytes BETWEEN 1 AND 20971520),
    CONSTRAINT ck_attachment_display_order CHECK (display_order BETWEEN 0 AND 4)
);
