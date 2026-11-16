package com.hackhu.hp.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * refresh 토큰 기록 (docs/spec/auth/backend/3-2-DESIGN-DB-SCHEMA.md 3-2-4). 원문은 저장하지 않고 SHA-256 해시만 둔다.
 *
 * <p>회전해도 행을 지우지 않는다. 응답 유실 복구와 재사용 감지가 예전 토큰의 기록을 보고 판단하기 때문이다 (4-2-5). 세션이 정리될 때 함께 지워진다.
 *
 * <p>상태를 바꾸는 메서드는 같은 세션의 {@code login_sessions} 행을 잠근 트랜잭션 안에서만 부른다 ({@code TokenRefreshService}).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    /** 마지막 갱신부터의 수명. 세션의 최대 수명을 넘지 않는다 (2-1). */
    public static final Duration LIFETIME = Duration.ofDays(14);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID sessionId;

    // CHAR(64)로 저장한다. SHA-256 16진수는 항상 64자다.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 64)
    private String tokenHash;

    private Instant expiresAt;

    private Long replacedById;

    private Instant replacedAt;

    private Instant revokedAt;

    @Generated private Instant createdAt;

    /**
     * 세션에 새 토큰을 발급한다. 만료는 14일과 세션의 남은 수명 중 짧은 쪽이다.
     *
     * @param tokenHash 토큰 원문의 SHA-256 해시 (16진수 64자)
     */
    public static RefreshToken issue(LoginSession session, String tokenHash, Instant now) {
        RefreshToken token = new RefreshToken();
        token.sessionId = session.getId();
        token.tokenHash = tokenHash;
        Instant max = now.plus(LIFETIME);
        token.expiresAt = max.isBefore(session.getExpiresAt()) ? max : session.getExpiresAt();
        return token;
    }

    /** 세션의 현재 토큰인지. 회전하지도 폐기하지도 않은 토큰이다. */
    public boolean isCurrent() {
        return replacedAt == null && revokedAt == null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** 회전한 지 {@code window}가 지나지 않았는지. 폐기한 토큰은 해당하지 않는다. */
    public boolean wasReplacedWithin(Duration window, Instant now) {
        return revokedAt == null && replacedAt != null && now.isBefore(replacedAt.plus(window));
    }

    /** {@code current}의 바로 앞 토큰인지. 폐기한 토큰은 해당하지 않는다. */
    public boolean isPredecessorOf(RefreshToken current) {
        return revokedAt == null && replacedById != null && replacedById.equals(current.getId());
    }

    /** 회전했다고 기록한다. 다음 토큰을 저장한 뒤 {@link #linkReplacement}로 잇는다. */
    public void markReplaced(Instant now) {
        replacedAt = now;
    }

    public void linkReplacement(RefreshToken next) {
        replacedById = next.getId();
    }

    /** 응답 유실 복구로 이 토큰만 폐기한다. 다시 쓰이면 재사용으로 본다 (4-2-5). */
    public void revoke(Instant now) {
        revokedAt = now;
    }
}
