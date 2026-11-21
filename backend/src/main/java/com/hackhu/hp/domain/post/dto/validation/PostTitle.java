package com.hackhu.hp.domain.post.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 글 제목 규칙 (docs/spec/board/backend/2-REQUIREMENTS.md 2-1, 5-1-QUALITY-SECURITY.md 5-1-5).
 *
 * <p>코드 포인트로 100자 이하이고 제어 문자와 양방향 텍스트 제어 문자가 없어야 한다. 앞뒤 공백은 DTO가 먼저 지운다. 비었는지는 검사하지 않으므로
 * {@code @NotBlank}와 함께 붙인다.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PostTitleValidator.class)
public @interface PostTitle {

    String message() default "제목은 100자 이하로, 줄바꿈이나 특수 제어 문자 없이 입력해주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
