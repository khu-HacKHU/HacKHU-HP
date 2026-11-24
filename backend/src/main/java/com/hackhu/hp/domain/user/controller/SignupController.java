package com.hackhu.hp.domain.user.controller;

import static com.hackhu.hp.global.security.AuthRateLimits.SIGNUP_ATTEMPTS_PER_IP;

import com.hackhu.hp.domain.user.dto.SignupRequest;
import com.hackhu.hp.domain.user.dto.SignupResponse;
import com.hackhu.hp.domain.user.service.SignupService;
import com.hackhu.hp.global.error.TooManyRequestsException;
import com.hackhu.hp.global.security.ClientIpResolver;
import com.hackhu.hp.global.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입 API. 공개 API다 (SecurityConfig의 공개 목록).
 *
 * <p>경로는 {@code /api/v1/auth}지만 회원을 만드는 일이라 {@code domain/user}에 둔다 (backend/AGENTS.md 패키지 구조).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;
    private final ClientIpResolver clientIpResolver;
    private final RateLimiter rateLimiter;

    // 본문은 application/json만 받는다. 다른 형식은 415다 (4-2-1).
    @PostMapping(path = "/api/v1/auth/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignupResponse> signup(
            @Valid @RequestBody SignupRequest request, HttpServletRequest httpRequest) {
        checkRateLimit(clientIpResolver.resolve(httpRequest));
        SignupResponse response = signupService.signup(request);
        // 가입한 사람이 로그인한 뒤 자기 정보를 보는 곳이다. 로그인은 따로 한다 (4-2-3).
        return ResponseEntity.created(URI.create("/api/v1/users/me")).body(response);
    }

    // IP 하나로 1시간에 50번까지 받는다. bcrypt 계산으로 CPU를 쓰게 하는 것과 대량 계정 확인을 늦춘다 (5-1-7, 5-1-11).
    private void checkRateLimit(String clientIp) {
        RateLimiter.Decision decision = rateLimiter.tryAcquire(SIGNUP_ATTEMPTS_PER_IP, clientIp);
        if (decision.allowed()) {
            return;
        }
        // 창마다 처음 거부할 때만 남긴다 (CWE-779).
        if (decision.firstRejection()) {
            log.info("가입 요청 제한: ip={}", clientIp);
        }
        throw new TooManyRequestsException(decision.retryAfter());
    }
}
