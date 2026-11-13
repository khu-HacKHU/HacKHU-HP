package com.hackhu.hp.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 작업을 켠다. 예: 오래된 로그인 세션 정리 ({@code LoginSessionCleanup}). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
