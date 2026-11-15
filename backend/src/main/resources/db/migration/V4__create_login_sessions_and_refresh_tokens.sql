-- 로그인 세션과 refresh 토큰 (docs/spec/auth/backend/3-2-DESIGN-DB-SCHEMA.md 3-2-3, 3-2-4).
-- V2, V3은 갤러리와 게시판 PR이 쓰고 있어서 V4로 둔다. 번호가 겹치면 늦게 머지하는 쪽이 올린다 (backend/AGENTS.md).

-- 로그인 한 번이 행 하나다. access 토큰의 sid가 이 행의 id이고 요청마다 이 행이 살아 있는지 본다.
-- id는 access 토큰에 실리므로 추측할 수 없는 무작위 UUID를 서버가 만든다.
CREATE TABLE login_sessions
(
    id            UUID PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ,
    revoke_reason VARCHAR(30),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_login_sessions_revoke_reason CHECK (
        revoke_reason IN ('LOGOUT', 'PASSWORD_CHANGED', 'PASSWORD_RESET', 'REUSE_DETECTED')
        ),
    -- 폐기 시각과 사유는 함께 있거나 함께 없다.
    CONSTRAINT ck_login_sessions_revoked_fields CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL))
);

-- 사용자의 모든 로그인 세션 폐기.
CREATE INDEX ix_login_sessions_user_id ON login_sessions (user_id);
-- 오래된 기록 정리 (3-2-7).
CREATE INDEX ix_login_sessions_expires_at ON login_sessions (expires_at);

-- refresh 토큰. 원문은 저장하지 않고 SHA-256 해시(16진수 64자)만 둔다.
-- 회전으로 이어진 예전 토큰의 기록은 세션이 끝날 때까지 남긴다. 응답 유실 복구와 재사용 감지가 이 기록을 본다 (4-2-5).
CREATE TABLE refresh_tokens
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id     UUID        NOT NULL REFERENCES login_sessions (id) ON DELETE CASCADE,
    token_hash     CHAR(64)    NOT NULL,
    expires_at     TIMESTAMPTZ NOT NULL,
    replaced_by_id BIGINT REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    replaced_at    TIMESTAMPTZ,
    revoked_at     TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- 세션의 현재 토큰 찾기.
CREATE INDEX ix_refresh_tokens_session_id ON refresh_tokens (session_id);
-- 한 세션의 현재 토큰(회전하지도 폐기하지도 않은 토큰)은 많아야 하나다 (3-2-4).
-- 세션 행 잠금이 회전을 하나씩 처리하지만, 코드 실수로 현재 토큰이 둘 생기는 것을 DB가 한 번 더 막는다.
CREATE UNIQUE INDEX uk_refresh_tokens_current ON refresh_tokens (session_id)
    WHERE replaced_at IS NULL AND revoked_at IS NULL;
-- 오래된 기록 정리 (3-2-7).
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);
