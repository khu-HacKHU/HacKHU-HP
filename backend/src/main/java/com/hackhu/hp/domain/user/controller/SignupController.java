package com.hackhu.hp.domain.user.controller;

import com.hackhu.hp.domain.user.dto.SignupRequest;
import com.hackhu.hp.domain.user.dto.SignupResponse;
import com.hackhu.hp.domain.user.service.SignupService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
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
@RestController
@RequiredArgsConstructor
public class SignupController {

    private final SignupService signupService;

    // 본문은 application/json만 받는다. 다른 형식은 415다 (4-2-1).
    @PostMapping(path = "/api/v1/auth/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = signupService.signup(request);
        // 가입한 사람이 로그인한 뒤 자기 정보를 보는 곳이다. 로그인은 따로 한다 (4-2-3).
        return ResponseEntity.created(URI.create("/api/v1/users/me")).body(response);
    }
}
