package com.hackhu.hp.domain.auth.service;

import com.hackhu.hp.domain.auth.repository.LoginSessionRepository;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 끝난 로그인 세션 정리 (docs/spec/auth/backend/3-2-DESIGN-DB-SCHEMA.md 3-2-7).
 *
 * <p>매일 한 번, 만료되었거나 폐기된 지 하루가 지난 세션을 지운다. refresh 토큰 기록은 CASCADE로 함께 지워진다. 살아 있는 세션의 예전 토큰은 지우지
 * 않는다. 응답 유실 복구와 재사용 감지가 그 기록을 본다.
 *
 * <p>서버가 여러 대여도 같은 조건으로 지우므로 겹쳐 돌아도 결과가 같다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class LoginSessionCleanup {

    static final Duration RETENTION_AFTER_END = Duration.ofDays(1);

    private final LoginSessionRepository loginSessionRepository;
    private final Clock clock;

    @Scheduled(cron = "0 30 4 * * *", zone = "UTC")
    void deleteEndedSessions() {
        int deleted =
                loginSessionRepository.deleteEndedBefore(
                        clock.instant().minus(RETENTION_AFTER_END));
        log.info("끝난 로그인 세션 정리: deleted={}", deleted);
    }
}
