package com.hackhu.hp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Spring Boot가 만드는 기본 계정(user / 시작 로그에 찍히는 비밀번호)을 쓰지 않는다. 회원은 users 테이블에만 있다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HpApplication {

    public static void main(String[] args) {
        SpringApplication.run(HpApplication.class, args);
    }
}
