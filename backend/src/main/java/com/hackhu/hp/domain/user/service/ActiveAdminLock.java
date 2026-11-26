package com.hackhu.hp.domain.user.service;

import com.hackhu.hp.domain.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마지막 활성 관리자 잠금 (docs/spec/auth/backend/5-2-QUALITY-CONCURRENCY.md 5-2-4).
 *
 * <p>활성 관리자의 역할을 내리거나 활성 관리자가 탈퇴하는 작업이 대상을 바꾸기 전에 부른다. 잠금은 호출한 트랜잭션이 끝날 때 풀리므로, 대상을 바꾸는 쿼리도 같은
 * 트랜잭션에서 실행한다.
 */
@Component
@RequiredArgsConstructor
public class ActiveAdminLock {

    private final UserRepository userRepository;

    /** 활성 관리자 행을 모두 잠근 뒤 대상의 상태를 돌려준다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Status lockAndCheck(long targetId) {
        List<Long> adminIds = userRepository.lockActiveAdminIds();
        if (!adminIds.contains(targetId)) {
            return Status.NOT_ACTIVE_ADMIN;
        }
        return adminIds.size() == 1 ? Status.LAST_ADMIN : Status.OTHER_ADMIN_EXISTS;
    }

    public enum Status {
        /** 잠그고 보니 대상이 활성 관리자가 아니다. 다시 읽어 비관리자 경로로 처리한다. */
        NOT_ACTIVE_ADMIN,
        /** 대상이 마지막 활성 관리자다. 역할을 내리거나 탈퇴할 수 없다. */
        LAST_ADMIN,
        /** 대상 말고 다른 활성 관리자가 있다. 같은 트랜잭션에서 대상을 바꿔도 된다. */
        OTHER_ADMIN_EXISTS
    }
}
