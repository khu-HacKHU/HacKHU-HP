package com.hackhu.hp.global.auth;

import java.util.Optional;

/**
 * access 토큰으로 현재 사용자를 정한다 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-4).
 *
 * <p>access 토큰 검증 필터({@code global/security})가 부른다. 구현은 로그인 세션과 계정을 읽어야 해서 {@code domain/auth}에 있다.
 * 필터가 도메인 패키지에 기대지 않게 여기에 인터페이스만 둔다.
 */
public interface AccessTokenAuthenticator {

    /**
     * 토큰의 서명과 만료를 확인하고, {@code sid}의 로그인 세션과 그 계정을 DB에서 한 번에 읽어 현재 사용자를 만든다.
     *
     * @return 받아 줄 수 없는 토큰이면 비어 있음. 세션이 없거나, 폐기되었거나, 만료되었거나, 세션의 사용자가 {@code sub}와 다르거나, 계정이 탈퇴한
     *     경우다. 이유는 로그에만 남긴다
     */
    Optional<AuthenticatedSession> authenticate(String accessToken);

    /**
     * 인증을 마친 요청.
     *
     * @param user DB에서 읽은 역할로 만든 현재 사용자
     * @param mustChangePassword 비밀번호 변경 필요 상태인지. 그러면 필터가 일부 API만 통과시킨다 (5-1-3)
     */
    record AuthenticatedSession(LoginUser user, boolean mustChangePassword) {}
}
