-- 게시판 글 (docs/spec/board/backend/3-2-DESIGN-DB-SCHEMA.md 3-2-2).
-- 세 게시판이 테이블 하나를 같이 쓰고 board_type으로 구분한다 (DECISIONS.md B1). 지금은 공지(NOTICE)만 있다.
-- 시각 컬럼은 TIMESTAMPTZ로 저장하고 UTC로 다룬다 (backend/AGENTS.md "시각 컬럼").
-- users 테이블과 set_updated_at() 함수가 V1__create_users.sql에 있으므로 그 뒤에 낸다 (3-2-4).
-- V2는 갤러리(#107)가 쓰고 있다. 번호가 겹치면 늦게 머지하는 쪽이 올린다 (backend/AGENTS.md).
CREATE TABLE post
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    board_type VARCHAR(20)  NOT NULL,
    title      VARCHAR(100) NOT NULL,
    content    TEXT         NOT NULL,
    author_id  BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- 정의되지 않은 게시판 종류를 막는다. 자유게시판과 자료게시판 값은 #53, #55에서 더한다.
    CONSTRAINT ck_post_board_type CHECK (board_type IN ('NOTICE')),
    -- 애플리케이션 검사를 빠뜨려도 빈 제목과 지나치게 긴 본문이 저장되지 않는다 (2-REQUIREMENTS.md 2-1).
    CONSTRAINT ck_post_title_length CHECK (char_length(title) BETWEEN 1 AND 100),
    CONSTRAINT ck_post_content_length CHECK (char_length(content) BETWEEN 1 AND 10000),
    -- 탈퇴는 익명화라 users 행이 남고, 행을 지우는 거절은 글을 쓸 수 없는 승인 대기 회원뿐이다 (auth 3-2-6).
    CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES users (id)
);

-- 게시판별 최신순 목록. 작성 시각이 같으면 id가 큰 글이 먼저 와서 페이지가 겹치지 않는다.
CREATE INDEX ix_post_board_type_created_at_id ON post (board_type, created_at DESC, id DESC);

-- 작성자의 글 찾기, 외래 키 참조 확인.
CREATE INDEX ix_post_author_id ON post (author_id);

-- updated_at은 트리거가 갱신한다. 코드에서 직접 쓰지 않는다 (auth DECISIONS.md A19).
CREATE TRIGGER trg_post_set_updated_at
    BEFORE UPDATE
    ON post
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
