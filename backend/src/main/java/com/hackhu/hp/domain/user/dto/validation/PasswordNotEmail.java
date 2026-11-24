package com.hackhu.hp.domain.user.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 비밀번호가 이메일과 같으면 거부한다. 대소문자는 무시한다 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-6).
 *
 * <p>요청 DTO에 붙이고, 사유는 {@code password} 칸에 담는다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordNotEmailValidator.class)
public @interface PasswordNotEmail {

    String message() default "비밀번호는 이메일과 다르게 입력해주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
