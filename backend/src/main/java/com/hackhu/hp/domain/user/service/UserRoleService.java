package com.hackhu.hp.domain.user.service;

import com.hackhu.hp.domain.user.dto.AdminUserResponse;
import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.domain.user.exception.UserErrorCode;
import com.hackhu.hp.domain.user.repository.UserRepository;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.error.BusinessException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 역할 변경 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-13, 5-2-QUALITY-CONCURRENCY.md
 * 5-2-4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private static final Set<Role> ADMIN_PATH_ROLES = Set.copyOf(EnumSet.of(Role.ADMIN));
    private static final Set<Role> NON_ADMIN_PATH_ROLES =
            Set.copyOf(EnumSet.of(Role.MEMBER, Role.STAFF));

    private final UserRepository userRepository;
    private final ActiveAdminLock activeAdminLock;
    private final TransactionTemplate transactionTemplate;

    /**
     * 대상의 역할을 바꾼다. 역할은 요청마다 DB에서 읽으므로 대상의 다음 요청부터 반영된다 (A2).
     *
     * <p>처음 읽은 역할로 경로를 고른 뒤 그 사이 역할이 바뀌었으면, 새 트랜잭션에서 다시 읽어 한 번만 다시 처리한다. 다시 처리해도 바뀌면 409다.
     *
     * @param newRole {@code MEMBER}, {@code STAFF}, {@code ADMIN} 중 하나
     * @throws BusinessException 대상이 없거나 탈퇴했으면 404, 승인 대기 회원이거나 마지막 활성 관리자이거나 경합이 계속되면 409
     */
    public AdminUserResponse changeRole(long actorId, long targetId, Role newRole) {
        Attempt attempt = attemptInTransaction(targetId, newRole);
        if (attempt.result() == Result.CONFLICT) {
            attempt = attemptInTransaction(targetId, newRole);
        }
        // 커밋이 끝난 뒤에 남긴다. 이메일과 이름은 남기지 않는다 (5-1-9, 5-1-10, CWE-532).
        log.info(
                "역할 변경: actorId={}, targetId={}, from={}, to={}, result={}",
                actorId,
                targetId,
                attempt.previousRole(),
                newRole,
                attempt.result());
        if (attempt.result().errorCode != null) {
            throw new BusinessException(attempt.result().errorCode);
        }
        return attempt.response();
    }

    private Attempt attemptInTransaction(long targetId, Role newRole) {
        return transactionTemplate.execute(status -> attempt(targetId, newRole));
    }

    private Attempt attempt(long targetId, Role newRole) {
        Optional<User> found =
                userRepository.findById(targetId).filter(user -> user.getWithdrawnAt() == null);
        if (found.isEmpty()) {
            return Attempt.failed(Result.NOT_FOUND, null);
        }
        User target = found.get();
        Role current = target.getRole();
        if (current == Role.PENDING) {
            return Attempt.failed(Result.TARGET_PENDING, current);
        }
        if (current == newRole) {
            return new Attempt(Result.UNCHANGED, current, AdminUserResponse.from(target));
        }
        if (current == Role.ADMIN) {
            ActiveAdminLock.Status status = activeAdminLock.lockAndCheck(targetId);
            if (status == ActiveAdminLock.Status.LAST_ADMIN) {
                return Attempt.failed(Result.LAST_ADMIN, current);
            }
            if (status == ActiveAdminLock.Status.NOT_ACTIVE_ADMIN) {
                return Attempt.failed(Result.CONFLICT, current);
            }
        }
        Set<Role> expectedRoles = current == Role.ADMIN ? ADMIN_PATH_ROLES : NON_ADMIN_PATH_ROLES;
        if (userRepository.updateRoleIfCurrentIn(targetId, newRole, expectedRoles) == 0) {
            return Attempt.failed(Result.CONFLICT, current);
        }
        // 조건부 갱신이 영속성 컨텍스트를 비웠으므로 바뀐 값을 다시 읽는다.
        User updated = userRepository.findById(targetId).orElseThrow();
        return new Attempt(Result.CHANGED, current, AdminUserResponse.from(updated));
    }

    // 로그의 result 값이다 (5-3-8).
    private enum Result {
        CHANGED(null),
        UNCHANGED(null),
        NOT_FOUND(UserErrorCode.AUTH_USER_NOT_FOUND),
        TARGET_PENDING(UserErrorCode.AUTH_TARGET_PENDING),
        LAST_ADMIN(UserErrorCode.AUTH_LAST_ADMIN),
        CONFLICT(UserErrorCode.AUTH_ROLE_CHANGE_CONFLICT);

        private final UserErrorCode errorCode;

        Result(UserErrorCode errorCode) {
            this.errorCode = errorCode;
        }
    }

    private record Attempt(Result result, Role previousRole, AdminUserResponse response) {

        private static Attempt failed(Result result, Role previousRole) {
            return new Attempt(result, previousRole, null);
        }
    }
}
