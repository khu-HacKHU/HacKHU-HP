-- album.uploader_id에 users FK를 건다 (docs/spec/gallery/backend/3-2-DESIGN-DB-SCHEMA.md 1절, #143).
-- V2에서는 users 테이블이 없어서 걸지 못했고, V1이 머지되어 조건이 충족됐다.

-- 탈퇴해도 users 행은 남고 개인정보 필드만 NULL이 되므로(auth 3-2-2 ck_users_withdrawn_fields)
-- 참조가 끊길 일이 없다. 그래서 ON DELETE 옵션을 두지 않는다. 앨범은 올린 사람이 탈퇴해도 남는다.
ALTER TABLE album
    ADD CONSTRAINT fk_album_uploader
        FOREIGN KEY (uploader_id) REFERENCES users (id);
