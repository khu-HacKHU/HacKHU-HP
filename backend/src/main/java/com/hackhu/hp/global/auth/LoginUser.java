package com.hackhu.hp.global.auth;

import java.util.Objects;
import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * 요청을 보낸 로그인 사용자 (docs/spec/auth/backend/3-1-DESIGN-SOFTWARE-ARCHITECTURE.md).
 *
 * <p>컨트롤러에서 {@link CurrentUser}로 받고, 서비스에는 인자로 넘긴다. 역할은 요청마다 DB에서 읽은 값이라 역할 변경이 바로 반영된다
 * (DECISIONS.md A2).
 *
 * <p>{@code id}는 {@code long}이다. 엔티티의 {@code Long authorId}와 {@code ==}로 비교해도 값으로 비교된다. 단,
 * 비교 대상이 null이면 언박싱할 때 예외가 나므로 nullable한 값은 {@link Objects#equals(Object, Object)}로 비교한다.
 *
 * @param id 사용자 ID ({@code users.id})
 * @param role DB에서 읽은 역할
 */
public record LoginUser(long id, Role role) implements AuthenticatedPrincipal {

    public LoginUser {
        Objects.requireNonNull(role, "role");
    }

    /** 역할이 {@code required} 이상인지. 승인 대기 회원은 {@code MEMBER} 이상이 아니다. */
    public boolean hasRoleAtLeast(Role required) {
        return role.isAtLeast(required);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** principal 이름은 사용자 ID다. 권한 거부 로그가 이 값을 남긴다 (5-1-9, 5-1-10). */
    @Override
    public String getName() {
        return String.valueOf(id);
    }
}
