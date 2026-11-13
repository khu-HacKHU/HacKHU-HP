package com.hackhu.hp.domain.user.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 회원 이름 규칙 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-8).
 *
 * <p>30자 이하이고 제어 문자와 양방향 텍스트 제어 문자가 없어야 한다. 앞뒤 공백은 DTO가 먼저 지운다. 비었는지는 검사하지 않으므로 {@code @NotBlank}와
 * 함께 붙인다.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PersonNameValidator.class)
public @interface PersonName {

    String message() default "이름은 30자 이하로 입력해주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
