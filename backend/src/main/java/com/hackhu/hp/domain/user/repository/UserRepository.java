package com.hackhu.hp.domain.user.repository;

import com.hackhu.hp.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 활성 계정이 이 이메일을 쓰는지. 탈퇴한 계정은 email이 NULL이라 걸리지 않는다. */
    boolean existsByEmail(String email);

    /** 이 이메일을 쓰는 활성 계정. 탈퇴한 계정은 email이 NULL이라 나오지 않는다. */
    Optional<User> findByEmail(String email);

    /**
     * 일반 로그인. 비밀번호를 검증한 뒤 활성 계정의 행을 잠그고 지금 해시를 읽는다 (5-2-3).
     *
     * <p>읽은 해시가 검증한 해시와 같을 때만 세션을 만든다. 검증과 세션 생성 사이에 비밀번호가 바뀌었으면 해시가 달라 실패한다. 아직 커밋하지 않은 변경은 이 잠금이
     * 기다린다.
     */
    @Query(
            value =
                    "SELECT password_hash FROM users WHERE id = :id AND withdrawn_at IS NULL FOR UPDATE",
            nativeQuery = true)
    Optional<String> findPasswordHashForUpdate(@Param("id") long id);

    /**
     * 임시 비밀번호 로그인. 검증한 해시가 아직 현재 값이고, 쓰지 않았고, 만료되지 않았을 때만 사용으로 기록한다 (5-2-3).
     *
     * <p>같은 임시 비밀번호로 동시에 로그인해도 한 요청만 1행을 바꾼다. 검증 직후 새 임시 비밀번호가 발급되면 해시가 달라 0행이다.
     *
     * @return 바뀐 행 수. 1일 때만 세션을 만든다
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    UPDATE users
                       SET temp_password_used_at = now()
                     WHERE id = :id
                       AND password_hash = :verifiedHash
                       AND withdrawn_at IS NULL
                       AND temp_password_used_at IS NULL
                       AND temp_password_expires_at > now()
                    """,
            nativeQuery = true)
    int markTemporaryPasswordUsed(@Param("id") long id, @Param("verifiedHash") String verifiedHash);
}
