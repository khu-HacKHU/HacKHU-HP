package com.hackhu.hp.domain.user.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호 규칙 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-6). 가입, 비밀번호 변경이 같이 쓴다.
 *
 * <p>10자 이상 64자 이하, UTF-8로 72바이트 이하. 조합 규칙은 두지 않는다 (DECISIONS.md A8). 비었는지는 검사하지 않으므로
 * {@code @NotBlank}와 함께 붙인다. 이메일과 같은지는 {@link PasswordNotEmail}이 본다.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
public @interface Password {

    String message() default "비밀번호는 10자 이상 64자 이하로 입력해주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
