package com.hackhu.hp.domain.post.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 글 본문 규칙 (docs/spec/board/backend/2-REQUIREMENTS.md 2-1, 5-1-QUALITY-SECURITY.md 5-1-5).
 *
 * <p>코드 포인트로 10,000자 이하이고 줄바꿈과 탭을 뺀 제어 문자가 없어야 한다. 비었는지는 검사하지 않으므로 {@code @NotBlank}와 함께 붙인다.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PostContentValidator.class)
public @interface PostContent {

    String message() default "본문은 10,000자 이하로, 특수 제어 문자 없이 입력해주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
