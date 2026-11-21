package com.hackhu.hp.domain.user.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 처음 관리자 ({@code app.initial-admin}, docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-6).
 *
 * <p>활성 관리자가 0명일 때만 쓴다. 값은 환경변수 {@code INITIAL_ADMIN_EMAIL}, {@code INITIAL_ADMIN_NAME}, {@code
 * INITIAL_ADMIN_PASSWORD}로만 받고 설정 파일에 적지 않는다. 운영에서는 세 값을 모두 Secrets Manager에 둔다 (DEPLOYMENT.md 4절).
 *
 * @param email 관리자 이메일. 가입과 같은 규칙으로 정규화하고 검사한다
 * @param name 관리자 이름
 * @param password 초기 비밀번호. 첫 로그인에서 바꿔야 한다
 * @param required 활성 관리자가 0명인데 값이 없을 때 서버를 멈출지. local과 테스트에서만 끈다
 */
@ConfigurationProperties("app.initial-admin")
public record InitialAdminProperties(String email, String name, String password, boolean required) {

    /** 세 값이 모두 비었는지. */
    boolean isEmpty() {
        return isBlank(email) && isBlank(name) && isBlank(password);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 이메일, 이름, 비밀번호가 로그나 예외 메시지에 찍히지 않게 한다 (5-1-9, CWE-532).
    @Override
    public String toString() {
        return "InitialAdminProperties[email=***, name=***, password=***, required="
                + required
                + "]";
    }
}
