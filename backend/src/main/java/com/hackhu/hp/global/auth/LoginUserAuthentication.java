package com.hackhu.hp.global.auth;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 인증을 마친 요청의 {@code Authentication}. access 토큰 검증 필터가 DB에서 로그인 세션과 계정을 읽은 뒤 이 객체를 SecurityContext에
 * 넣는다 (5-1-4).
 *
 * <p>권한은 역할 하나만 담는다. 상위 역할이 하위 역할을 포함하는 것은 역할 계층이 처리한다. 토큰 원문은 들고 있지 않는다.
 */
public final class LoginUserAuthentication extends AbstractAuthenticationToken {

    private final LoginUser principal;

    public LoginUserAuthentication(LoginUser principal) {
        super(List.of(new SimpleGrantedAuthority(principal.role().authority())));
        this.principal = principal;
        super.setAuthenticated(true);
    }

    @Override
    public LoginUser getPrincipal() {
        return principal;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
