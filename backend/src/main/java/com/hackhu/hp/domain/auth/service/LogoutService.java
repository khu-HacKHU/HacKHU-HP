package com.hackhu.hp.domain.auth.service;

import com.hackhu.hp.domain.auth.entity.SessionRevokeReason;
import com.hackhu.hp.domain.auth.repository.LoginSessionRepository;
import com.hackhu.hp.domain.auth.repository.RefreshTokenRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그아웃 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-6).
 *
 * <p>refresh 쿠키의 로그인 세션 하나만 폐기한다. 그 세션의 access 토큰은 만료 전이라도 다음 요청부터 401을 받는다. 같은 브라우저의 다른 탭도 같은 세션이라
 * 함께 로그아웃되고, 다른 기기의 로그인은 유지된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final LoginSessionRepository loginSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    /** 쿠키가 없거나, 모르는 토큰이거나, 이미 폐기된 세션이어도 조용히 끝난다. 컨트롤러는 항상 204를 준다. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        // 회전한 예전 토큰이어도 같은 세션의 토큰이면 그 세션을 폐기한다.
        refreshTokenRepository
                .findSessionIdByTokenHash(RefreshTokenValues.hash(refreshToken))
                .ifPresent(
                        sessionId -> {
                            if (loginSessionRepository.revoke(
                                            sessionId, SessionRevokeReason.LOGOUT, clock.instant())
                                    == 1) {
                                log.info("로그아웃: sessionId={}", sessionId);
                            }
                        });
    }
}
