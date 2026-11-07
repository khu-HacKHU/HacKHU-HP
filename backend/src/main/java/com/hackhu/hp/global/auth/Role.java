package com.hackhu.hp.global.auth;

/**
 * 회원 역할 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-1).
 *
 * <p>선언 순서가 역할의 높낮이다. {@code PENDING}, {@code MEMBER}, {@code STAFF}, {@code ADMIN} 순으로 높고, 높은 역할은
 * 낮은 역할이 할 수 있는 일을 모두 할 수 있다. Security의 역할 계층({@code SecurityConfig#roleHierarchy})도 이 순서로 만든다. 순서를
 * 바꾸면 권한이 바뀌므로 바꾸지 않는다.
 *
 * <p>방문자(비로그인)는 역할이 없다. 승인 대기 회원({@code PENDING})은 내 정보 API를 빼면 방문자와 같다 (PRODUCT.md 4절).
 */
public enum Role {
    PENDING,
    MEMBER,
    STAFF,
    ADMIN;

    /** 이 역할이 {@code required} 이상인지. */
    public boolean isAtLeast(Role required) {
        return compareTo(required) >= 0;
    }

    /** Spring Security 권한 이름. {@code hasRole("MEMBER")}가 이 값을 본다. */
    public String authority() {
        return "ROLE_" + name();
    }
}
