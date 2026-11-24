-- 회원 테이블 (docs/spec/auth/backend/3-2-DESIGN-DB-SCHEMA.md 3-2-2).
-- 다른 기능은 users.id만 외래 키로 참조한다.

-- updated_at을 행이 바뀔 때마다 DB가 갱신한다. 승인, 역할 변경처럼 조건부 UPDATE 쿼리로 바꾸는 작업이 많아
-- JPA의 @UpdateTimestamp만으로는 빠뜨리기 쉽다 (DECISIONS.md A19). 다른 테이블도 이 함수를 트리거로 건다.
CREATE FUNCTION set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TABLE users
(
    id                       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email                    VARCHAR(254),
    name                     VARCHAR(30),
    password_hash            VARCHAR(100),
    role                     VARCHAR(20) NOT NULL,
    must_change_password     BOOLEAN     NOT NULL DEFAULT false,
    temp_password_expires_at TIMESTAMPTZ,
    temp_password_used_at    TIMESTAMPTZ,
    approved_at              TIMESTAMPTZ,
    withdrawn_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 대소문자만 다른 이메일로 중복 가입하는 것을 막는다.
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_role CHECK (role IN ('PENDING', 'MEMBER', 'STAFF', 'ADMIN')),
    -- 활성 계정은 로그인 정보가 비지 않는다.
    CONSTRAINT ck_users_active_fields CHECK (
        withdrawn_at IS NOT NULL
            OR (email IS NOT NULL AND name IS NOT NULL AND password_hash IS NOT NULL)
        ),
    -- 탈퇴한 계정에 개인정보가 남지 않는다.
    CONSTRAINT ck_users_withdrawn_fields CHECK (
        withdrawn_at IS NULL
            OR (email IS NULL AND name IS NULL AND password_hash IS NULL)
        )
);

-- 활성 계정끼리만 이메일이 겹치지 않는다. 탈퇴하면 email이 NULL이 되어 같은 이메일로 다시 가입할 수 있다.
CREATE UNIQUE INDEX uk_users_email ON users (email) WHERE email IS NOT NULL;

-- 승인 대기 목록, 역할별 회원 목록, 활성 관리자 수 확인.
CREATE INDEX ix_users_role_created_at ON users (role, created_at);

CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE
    ON users
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
