-- global/storage. 갤러리와 자료게시판 첨부파일이 같이 쓰는 업로드 발급 기록 (docs/spec/gallery/backend/3-2 4절).
CREATE TABLE upload_record (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    object_key VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(50) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(10) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

-- 만료된 ISSUED 기록을 찾아 지우는 정리 배치가 쓸 인덱스.
CREATE INDEX idx_upload_record_status_expires_at ON upload_record (status, expires_at);
