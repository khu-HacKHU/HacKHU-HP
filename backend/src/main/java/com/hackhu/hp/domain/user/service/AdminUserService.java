package com.hackhu.hp.domain.user.service;

import com.hackhu.hp.domain.user.dto.AdminUserPageResponse;
import com.hackhu.hp.domain.user.dto.AdminUserResponse;
import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.domain.user.exception.UserErrorCode;
import com.hackhu.hp.domain.user.repository.UserRepository;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.error.BusinessException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 운영진의 회원 목록, 가입 승인, 가입 거절 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-10~12).
 *
 * <p>승인과 거절은 확인과 쓰기를 조건부 쿼리 하나로 한다. 0행이면 다시 읽어 404와 409를 나눈다 (5-2-2).
 *
 * <p>처리 기록은 커밋한 뒤에 남긴다. 처리한 사람 ID, 대상 ID, 결과만 적고 이메일과 이름은 적지 않는다 (5-1-9, 5-1-10).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final String SUCCESS = "SUCCESS";

    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param role 이 역할만 본다. null이면 모든 역할
     * @param direction 가입 시각 정렬 방향. 같은 시각이면 ID를 같은 방향으로 정렬해 페이지가 흔들리지 않게 한다
     */
    @Transactional(readOnly = true)
    public AdminUserPageResponse getUsers(Role role, int page, int size, Sort.Direction direction) {
        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                new Sort.Order(direction, "createdAt"),
                                new Sort.Order(direction, "id")));
        Page<User> users =
                role == null
                        ? userRepository.findByWithdrawnAtIsNull(pageable)
                        : userRepository.findByRoleAndWithdrawnAtIsNull(role, pageable);
        return AdminUserPageResponse.from(users.map(AdminUserResponse::from));
    }

    /**
     * 승인 대기 회원을 동아리원으로 바꾼다. 역할은 요청마다 DB에서 읽으므로 그 사람의 다음 요청부터 적용된다 (A2).
     *
     * @throws BusinessException 대상이 없거나 탈퇴했으면 {@code AUTH_USER_NOT_FOUND}, 승인 대기가 아니면 {@code
     *     AUTH_USER_NOT_PENDING}
     */
    public AdminUserResponse approve(long actorId, long userId) {
        Optional<AdminUserResponse> approved =
                transactionTemplate.execute(status -> approveAndRead(userId));
        if (approved == null || approved.isEmpty()) {
            UserErrorCode failure = failureOf(userId);
            log.info("가입 승인: actorId={}, targetId={}, result={}", actorId, userId, failure.code());
            throw new BusinessException(failure);
        }
        log.info("가입 승인: actorId={}, targetId={}, result={}", actorId, userId, SUCCESS);
        return approved.get();
    }

    /**
     * 승인 대기 회원의 행을 지운다. 같은 이메일로 다시 가입할 수 있고, 그 사람의 세션과 토큰은 함께 사라진다 (3-2-5).
     *
     * @throws BusinessException 대상이 없거나 탈퇴했으면 {@code AUTH_USER_NOT_FOUND}, 승인 대기가 아니면 {@code
     *     AUTH_USER_NOT_PENDING}
     */
    public void reject(long actorId, long userId) {
        if (userRepository.deletePending(userId) == 0) {
            UserErrorCode failure = failureOf(userId);
            log.info("가입 거절: actorId={}, targetId={}, result={}", actorId, userId, failure.code());
            throw new BusinessException(failure);
        }
        log.info("가입 거절: actorId={}, targetId={}, result={}", actorId, userId, SUCCESS);
    }

    // 갱신한 트랜잭션 안에서 다시 읽는다. 행 잠금이 남아 있어 응답이 방금 승인한 상태와 같다.
    private Optional<AdminUserResponse> approveAndRead(long userId) {
        if (userRepository.approvePending(userId) == 0) {
            return Optional.empty();
        }
        return userRepository.findById(userId).map(AdminUserResponse::from);
    }

    // 조건부 쿼리가 0행일 때 이유를 나눈다. 승인된 회원이 다시 승인 대기로 돌아가는 경로는 없다 (5-1-3).
    private UserErrorCode failureOf(long userId) {
        boolean active =
                userRepository
                        .findById(userId)
                        .filter(user -> user.getWithdrawnAt() == null)
                        .isPresent();
        return active ? UserErrorCode.AUTH_USER_NOT_PENDING : UserErrorCode.AUTH_USER_NOT_FOUND;
    }
}
