package com.hackhu.hp.domain.auth.dto;

import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.global.auth.AccessTokenService.IssuedAccessToken;
import com.hackhu.hp.global.auth.Role;

/**
 * 로그인과 refresh 응답 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-4, 4-2-5). refresh 토큰은
 * 본문이 아니라 {@code Set-Cookie}로 준다.
 *
 * @param expiresIn access 토큰이 만료될 때까지 남은 초
 * @param user 프론트엔드가 새로고침 뒤 로그인 상태를 되살리고, refresh 뒤 원래 요청을 다시 보내도 되는지 확인하는 데 쓴다
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn, AuthUser user) {

    private static final String BEARER = "Bearer";

    public static TokenResponse of(IssuedAccessToken accessToken, User user) {
        return new TokenResponse(
                accessToken.value(), BEARER, accessToken.expiresInSeconds(), AuthUser.from(user));
    }

    // 토큰이 로그에 찍히지 않게 한다 (5-1-5).
    @Override
    public String toString() {
        return "TokenResponse[accessToken=***, user.id=" + user.id() + "]";
    }

    public record AuthUser(
            long id, String email, String name, Role role, boolean mustChangePassword) {

        static AuthUser from(User user) {
            return new AuthUser(
                    user.getId(),
                    user.getEmail(),
                    user.getName(),
                    user.getRole(),
                    user.isMustChangePassword());
        }

        @Override
        public String toString() {
            return "AuthUser[id=" + id + ", role=" + role + "]";
        }
    }
}
