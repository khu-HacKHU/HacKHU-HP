package com.hackhu.hp.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.auth.LoginUserAuthentication;
import com.hackhu.hp.global.auth.Role;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.GrantedAuthority;

class RoleHierarchyTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "PENDING | ROLE_PENDING",
                "MEMBER  | ROLE_MEMBER, ROLE_PENDING",
                "STAFF   | ROLE_STAFF, ROLE_MEMBER, ROLE_PENDING",
                "ADMIN   | ROLE_ADMIN, ROLE_STAFF, ROLE_MEMBER, ROLE_PENDING"
            })
    void 상위_역할은_하위_역할을_모두_가진다(Role role, String expected) {
        LoginUserAuthentication authentication =
                new LoginUserAuthentication(new LoginUser(1L, role));

        Set<String> reachable =
                SecurityConfig.roleHierarchy()
                        .getReachableGrantedAuthorities(authentication.getAuthorities())
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());

        assertThat(reachable).containsExactlyInAnyOrder(expected.split(",\\s*"));
    }
}
