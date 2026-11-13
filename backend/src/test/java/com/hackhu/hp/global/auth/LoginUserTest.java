package com.hackhu.hp.global.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LoginUserTest {

    @ParameterizedTest(name = "{0}은 {1} 이상인가: {2}")
    @CsvSource({
        "PENDING, PENDING, true",
        "PENDING, MEMBER, false",
        "MEMBER, MEMBER, true",
        "MEMBER, STAFF, false",
        "STAFF, MEMBER, true",
        "STAFF, ADMIN, false",
        "ADMIN, STAFF, true",
        "ADMIN, PENDING, true"
    })
    void 역할은_선언_순서로_비교한다(Role role, Role required, boolean expected) {
        assertThat(new LoginUser(1L, role).hasRoleAtLeast(required)).isEqualTo(expected);
    }

    @Test
    void 승인_대기_회원은_동아리원_이상이_아니다() {
        LoginUser pending = new LoginUser(1L, Role.PENDING);

        assertThat(pending.hasRoleAtLeast(Role.MEMBER)).isFalse();
        assertThat(pending.isAdmin()).isFalse();
    }

    @Test
    void 관리자만_isAdmin이다() {
        assertThat(new LoginUser(1L, Role.ADMIN).isAdmin()).isTrue();
        assertThat(new LoginUser(1L, Role.STAFF).isAdmin()).isFalse();
    }

    @Test
    void 역할_없이_만들_수_없다() {
        assertThatNullPointerException().isThrownBy(() -> new LoginUser(1L, null));
    }

    @Test
    void Authentication의_이름은_사용자_ID다() {
        // 권한 거부 로그가 authentication.getName()을 남긴다 (5-1-10)
        LoginUserAuthentication authentication =
                new LoginUserAuthentication(new LoginUser(42L, Role.MEMBER));

        assertThat(authentication.getName()).isEqualTo("42");
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getPrincipal()).isEqualTo(new LoginUser(42L, Role.MEMBER));
    }
}
