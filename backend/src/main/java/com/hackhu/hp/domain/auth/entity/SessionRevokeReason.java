package com.hackhu.hp.domain.auth.entity;

/** 로그인 세션을 폐기한 이유 ({@code login_sessions.revoke_reason}, 3-2-3). */
public enum SessionRevokeReason {
    /** 직접 로그아웃하거나, 같은 브라우저에서 다시 로그인함 */
    LOGOUT,
    /** 비밀번호를 바꿈 (#49) */
    PASSWORD_CHANGED,
    /** 운영진이 임시 비밀번호를 발급함 (#51) */
    PASSWORD_RESET,
    /** 회전한 refresh 토큰이 다시 쓰여 탈취로 봄 (4-2-5) */
    REUSE_DETECTED
}
