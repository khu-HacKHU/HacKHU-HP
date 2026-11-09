package com.hackhu.hp.domain.auth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.springframework.data.domain.Persistable;

/**
 * 로그인 세션 (docs/spec/auth/backend/3-2-DESIGN-DB-SCHEMA.md 3-2-3). 로그인 한 번이 행 하나이고 기기 하나의 로그인 상태에
 * 해당한다.
 *
 * <p>access 토큰의 {@code sid}가 이 행의 ID다. 요청마다 이 행이 살아 있는지 확인하므로, 폐기하면 그 세션의 access 토큰이 만료 전이라도 다음
 * 요청부터 거부된다 (DECISIONS.md A2).
 *
 * <p>폐기는 조건부 갱신 쿼리로 한다 ({@code LoginSessionRepository#revoke}).
 */
@Entity
@Table(name = "login_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginSession implements Persistable<UUID> {

    /** 로그인부터 최대 수명. 회전해도 늘어나지 않는다 (2-REQUIREMENTS.md 2-1). */
    public static final Duration MAX_LIFETIME = Duration.ofDays(30);

    @Id private UUID id;

    private Long userId;

    private Instant expiresAt;

    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    private SessionRevokeReason revokeReason;

    @Generated private Instant createdAt;

    // ID를 서버가 정하므로 Spring Data가 새 행인지 ID로 알 수 없다. 저장 전에는 새 행으로 알린다.
    @Transient private boolean isNew = true;

    /**
     * 새 로그인 세션. ID는 추측할 수 없는 무작위 UUID다 (3-2-1).
     *
     * @param now 로그인 시각
     */
    public static LoginSession start(long userId, Instant now) {
        LoginSession session = new LoginSession();
        session.id = UUID.randomUUID();
        session.userId = userId;
        session.expiresAt = now.plus(MAX_LIFETIME);
        return session;
    }

    /** 폐기되지 않았고 최대 수명이 지나지 않았는지. */
    public boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }
}
