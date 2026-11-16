package com.hackhu.hp.domain.auth.repository;

import com.hackhu.hp.domain.auth.entity.LoginSession;
import com.hackhu.hp.domain.auth.entity.SessionRevokeReason;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface LoginSessionRepository extends JpaRepository<LoginSession, UUID> {

    /**
     * 요청마다 하는 인증. 로그인 세션과 그 계정을 기본 키로 한 번에 읽는다 (5-1-4 5단계). 잠그지 않는다 (5-2-5).
     *
     * <p>거절로 계정 행이 지워지면 세션도 CASCADE로 지워져 결과가 없다.
     */
    @Query(
            """
            select new com.hackhu.hp.domain.auth.repository.SessionPrincipal(
                s.userId, s.expiresAt, s.revokedAt, u.role, u.mustChangePassword, u.withdrawnAt)
            from LoginSession s join User u on u.id = s.userId
            where s.id = :id
            """)
    Optional<SessionPrincipal> findPrincipal(@Param("id") UUID id);

    /** refresh의 세션 잠금. 같은 세션의 refresh, 로그아웃, 폐기를 하나씩 처리한다 (5-2-2). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LoginSession s where s.id = :id")
    Optional<LoginSession> findByIdForUpdate(@Param("id") UUID id);

    /**
     * 살아 있는 세션 하나를 폐기한다. 이미 폐기된 세션은 건드리지 않는다.
     *
     * @return 폐기한 행 수 (0 또는 1)
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query(
            """
            update LoginSession s set s.revokedAt = :now, s.revokeReason = :reason
            where s.id = :id and s.revokedAt is null
            """)
    int revoke(
            @Param("id") UUID id,
            @Param("reason") SessionRevokeReason reason,
            @Param("now") Instant now);

    /**
     * 만료되었거나 폐기된 지 {@code cutoff}보다 오래된 세션을 지운다. 그 세션의 refresh 토큰은 CASCADE로 함께 지워진다 (3-2-7).
     *
     * @return 지운 세션 수
     */
    @Transactional
    @Modifying
    @Query("delete from LoginSession s where s.expiresAt < :cutoff or s.revokedAt < :cutoff")
    int deleteEndedBefore(@Param("cutoff") Instant cutoff);
}
