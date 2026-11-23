package com.hackhu.hp.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.domain.user.exception.UserErrorCode;
import com.hackhu.hp.domain.user.repository.UserRepository;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.error.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 실제 DB로 만들기 어려운 "두 번 다 경합" 경로 (5-2-4, 6-15 RC12). 나머지는 RoleChangeIntegrationTest가 본다. */
class UserRoleServiceTest {

    private static final long ACTOR_ID = 1L;
    private static final long TARGET_ID = 2L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ActiveAdminLock activeAdminLock = mock(ActiveAdminLock.class);
    private final UserRoleService service =
            new UserRoleService(
                    userRepository,
                    activeAdminLock,
                    new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private static User userWithRole(Role role) {
        User user = User.signUp("target@example.com", "홍길동", "hash");
        ReflectionTestUtils.setField(user, "id", TARGET_ID);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    @Test
    void 조건부_갱신이_두_번_다_0행이면_409이고_세_번_시도하지_않는다() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(userWithRole(Role.STAFF)));
        when(userRepository.updateRoleIfCurrentIn(eq(TARGET_ID), eq(Role.MEMBER), anyCollection()))
                .thenReturn(0);

        BusinessException e =
                catchThrowableOfType(
                        BusinessException.class,
                        () -> service.changeRole(ACTOR_ID, TARGET_ID, Role.MEMBER));

        assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.AUTH_ROLE_CHANGE_CONFLICT);
        verify(userRepository, times(2))
                .updateRoleIfCurrentIn(eq(TARGET_ID), eq(Role.MEMBER), anyCollection());
    }

    @Test
    void 잠근_뒤_두_번_다_관리자가_아니면_409이고_갱신하지_않는다() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(userWithRole(Role.ADMIN)));
        when(activeAdminLock.lockAndCheck(TARGET_ID))
                .thenReturn(ActiveAdminLock.Status.NOT_ACTIVE_ADMIN);

        BusinessException e =
                catchThrowableOfType(
                        BusinessException.class,
                        () -> service.changeRole(ACTOR_ID, TARGET_ID, Role.MEMBER));

        assertThat(e.getErrorCode()).isEqualTo(UserErrorCode.AUTH_ROLE_CHANGE_CONFLICT);
        verify(activeAdminLock, times(2)).lockAndCheck(TARGET_ID);
        verify(userRepository, never()).updateRoleIfCurrentIn(anyLong(), any(), anyCollection());
    }
}
