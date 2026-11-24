package com.hackhu.hp.domain.user.dto;

import com.hackhu.hp.domain.user.dto.validation.Password;
import com.hackhu.hp.domain.user.dto.validation.PasswordNotEmail;
import com.hackhu.hp.domain.user.dto.validation.PersonName;
import com.hackhu.hp.domain.user.entity.Emails;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 가입 요청 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-3).
 *
 * <p>계약에 적힌 세 필드만 받는다. {@code role}, {@code id}, {@code approvedAt}을 두지 않는다. 본문에 넣어 보내도 무시되고 가입한
 * 사람은 항상 승인 대기 회원이 된다 (5-1-8, CWE-915).
 *
 * <p>정규화를 생성자에서 먼저 하고 그 결과를 검증한다. 이메일은 앞뒤 공백을 지우고 소문자로, 이름은 앞뒤 공백을 지운다. 비밀번호는 바꾸지 않는다.
 */
@PasswordNotEmail
public record SignupRequest(
        @NotBlank(message = "이메일을 입력해주세요.")
                @Size(max = 254, message = "이메일은 254자 이하로 입력해주세요.")
                // ASCII 밖의 문자는 Java와 PostgreSQL의 소문자 변환 결과가 달라 받지 않는다 (Emails#normalize).
                @Pattern(regexp = "^[\\x21-\\x7E]*$", message = "이메일은 영문, 숫자, 기호로만 입력해주세요.")
                @Email(message = "이메일 형식이 올바르지 않습니다.")
                String email,
        @NotBlank(message = "비밀번호를 입력해주세요.") @Password String password,
        @NotBlank(message = "이름을 입력해주세요.") @PersonName String name) {

    public SignupRequest {
        email = Emails.normalize(email);
        name = name == null ? null : name.strip();
    }

    // 비밀번호가 로그나 예외 메시지에 찍히지 않게 한다 (CWE-532). record의 기본 toString은 모든 필드를 담는다.
    @Override
    public String toString() {
        return "SignupRequest[email=***, password=***, name=***]";
    }
}
