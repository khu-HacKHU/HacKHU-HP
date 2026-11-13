package com.hackhu.hp.global.security;

import java.time.Duration;

/**
 * 인증권한 API의 요청 횟수 제한 규칙 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-7, CWE-307).
 *
 * <p>계정을 잠그지 않고, 이메일만을 기준으로 막지도 않는다. 막는 기준에는 모두 IP나 본인의 사용자 ID가 들어간다. 공격자가 남의 이메일로 일부러 실패해 그 사람이
 * 로그인하지 못하게 만드는 것을 막으려는 것이다 (DECISIONS.md A11).
 */
public final class AuthRateLimits {

    private AuthRateLimits() {}

    /** 로그인: IP와 이메일 조합으로 5분에 실패 10회. 가입되지 않은 이메일도 똑같이 센다. */
    public static final RateLimitRule LOGIN_FAILURES_PER_IP_AND_EMAIL =
            new RateLimitRule("login-ip-email", 10, Duration.ofMinutes(5));

    /** 로그인: IP 하나로 5분에 모든 시도 200회. 학교 와이파이처럼 IP를 함께 쓰는 경우를 생각해 넉넉히 잡는다. */
    public static final RateLimitRule LOGIN_ATTEMPTS_PER_IP =
            new RateLimitRule("login-ip", 200, Duration.ofMinutes(5));

    /** 가입: IP 하나로 1시간에 모든 시도 50회. 모집 날 한자리에서 50명이 가입해도 막히지 않는 값이다 (2-4). */
    public static final RateLimitRule SIGNUP_ATTEMPTS_PER_IP =
            new RateLimitRule("signup-ip", 50, Duration.ofHours(1));

    /**
     * 비밀번호 변경과 탈퇴: 사용자 ID로 5분에 지금 비밀번호가 틀린 시도 10회. 두 API가 같은 카운터를 함께 센다.
     *
     * <p>두 API는 #49에서 만든다. 지금 비밀번호를 검증하기 전에 {@link RateLimiter#tryAcquire}로 한 번을 잡고, 비밀번호가 맞으면
     * {@link RateLimiter#release}로 돌려준다. 로그인이 같은 방식으로 실패만 센다 ({@code LoginService}).
     */
    public static final RateLimitRule PASSWORD_CONFIRM_FAILURES_PER_USER =
            new RateLimitRule("password-confirm-user", 10, Duration.ofMinutes(5));
}
