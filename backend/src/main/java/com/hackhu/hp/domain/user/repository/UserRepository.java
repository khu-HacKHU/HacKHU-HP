package com.hackhu.hp.domain.user.repository;

import com.hackhu.hp.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 활성 계정이 이 이메일을 쓰는지. 탈퇴한 계정은 email이 NULL이라 걸리지 않는다. */
    boolean existsByEmail(String email);
}
