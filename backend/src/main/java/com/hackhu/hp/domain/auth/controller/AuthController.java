package com.hackhu.hp.domain.auth.controller;

import com.hackhu.hp.domain.auth.dto.LoginRequest;
import com.hackhu.hp.domain.auth.dto.TokenResponse;
import com.hackhu.hp.domain.auth.service.IssuedTokens;
import com.hackhu.hp.domain.auth.service.LoginService;
import com.hackhu.hp.domain.auth.service.LogoutService;
import com.hackhu.hp.domain.auth.service.TokenRefreshService;
import com.hackhu.hp.global.error.CommonErrorCode;
import com.hackhu.hp.global.error.ErrorResponse;
import com.hackhu.hp.global.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인, access 토큰 갱신, 로그아웃 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-4~4-2-6).
 *
 * <p>세 API 모두 공개 목록에 있고 access 토큰을 보지 않는다. {@code Origin} 허용 목록 검사는 컨트롤러에 오기 전에 {@code
 * AuthOriginFilter}가 한다 (5-1-5).
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final LoginService loginService;
    private final TokenRefreshService tokenRefreshService;
    private final LogoutService logoutService;
    private final ClientIpResolver clientIpResolver;

    // 본문은 application/json만 받는다. 다른 사이트의 폼으로 로그인을 위조하는 것을 막는다 (415, 4-2-1, A17).
    @PostMapping(path = "/api/v1/auth/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String existingToken,
            HttpServletRequest httpRequest) {
        IssuedTokens issued =
                loginService.login(
                        request.email(),
                        request.password(),
                        clientIpResolver.resolve(httpRequest),
                        existingToken);
        return withTokens(issued);
    }

    @PostMapping("/api/v1/auth/refresh")
    public ResponseEntity<?> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        return tokenRefreshService
                .refresh(refreshToken, clientIpResolver.resolve(httpRequest))
                .<ResponseEntity<?>>map(AuthController::withTokens)
                .orElseGet(AuthController::rejectRefresh);
    }

    // 쿠키가 없거나 이미 폐기된 세션이어도 204와 쿠키 삭제를 준다 (4-2-6).
    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken) {
        logoutService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, RefreshTokenCookies.clear())
                .build();
    }

    // 토큰을 담은 응답은 캐시하지 않는다 (5-1-5).
    private static ResponseEntity<TokenResponse> withTokens(IssuedTokens issued) {
        ResponseEntity.BodyBuilder response =
                ResponseEntity.ok().cacheControl(CacheControl.noStore());
        if (issued.refreshToken() != null) {
            response.header(
                    HttpHeaders.SET_COOKIE, RefreshTokenCookies.issue(issued.refreshToken()));
        }
        return response.body(issued.body());
    }

    // 모르는 토큰, 만료, 폐기된 세션, 재사용 모두 같은 401이고 쿠키를 지운다 (4-2-5).
    private static ResponseEntity<ErrorResponse> rejectRefresh() {
        CommonErrorCode code = CommonErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(code.status())
                .header(HttpHeaders.SET_COOKIE, RefreshTokenCookies.clear())
                .body(ErrorResponse.of(code));
    }
}
