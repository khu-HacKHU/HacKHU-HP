package com.hackhu.hp;

import org.springframework.boot.SpringApplication;

public class TestHpApplication {

    public static void main(String[] args) {
        SpringApplication.from(HpApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
