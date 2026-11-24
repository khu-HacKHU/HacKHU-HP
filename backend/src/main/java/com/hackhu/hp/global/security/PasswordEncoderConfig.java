package com.hackhu.hp.global.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 비밀번호 해시 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-6). 가입, 로그인, 비밀번호 변경이 같이 쓴다. */
@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

    // bcrypt cost 12. 해시 한 번에 약 0.3초 걸린다 (2-REQUIREMENTS.md 2-3, CWE-916).
    static final int BCRYPT_STRENGTH = 12;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }
}
