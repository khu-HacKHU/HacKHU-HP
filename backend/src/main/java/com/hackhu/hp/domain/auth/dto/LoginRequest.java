package com.hackhu.hp.domain.auth.dto;

import com.hackhu.hp.domain.user.entity.Emails;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-4).
 *
 * <p>비었는지만 검사한다. 형식이나 길이가 틀린 값은 가입된 계정과 맞지 않으므로 로그인 실패(401)로 끝난다. 이메일은 가입과 같은 규칙으로 정규화한다.
 */
public record LoginRequest(
        @NotBlank(message = "이메일을 입력해주세요.") String email,
        @NotBlank(message = "비밀번호를 입력해주세요.") String password) {

    public LoginRequest {
        email = Emails.normalize(email);
    }

    // 비밀번호와 이메일이 로그나 예외 메시지에 찍히지 않게 한다 (5-1-9, CWE-532).
    @Override
    public String toString() {
        return "LoginRequest[email=***, password=***]";
    }
}
