package com.hackhu.hp.domain.auth.repository;

import com.hackhu.hp.domain.auth.entity.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 토큰이 속한 세션 ID. 엔티티를 읽지 않는다.
     *
     * <p>refresh는 이 값으로 세션을 먼저 잠근 뒤 토큰을 읽는다. 잠그기 전에 토큰 엔티티를 읽으면 같은 트랜잭션에 잠금 전의 상태가 남는다.
     */
    @Query("select t.sessionId from RefreshToken t where t.tokenHash = :tokenHash")
    Optional<UUID> findSessionIdByTokenHash(@Param("tokenHash") String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 세션의 현재 토큰. 회전하지도 폐기하지도 않은 토큰은 많아야 하나다 ({@code uk_refresh_tokens_current}). */
    @Query(
            """
            select t from RefreshToken t
            where t.sessionId = :sessionId and t.replacedAt is null and t.revokedAt is null
            """)
    Optional<RefreshToken> findCurrent(@Param("sessionId") UUID sessionId);
}
