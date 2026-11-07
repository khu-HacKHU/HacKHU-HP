package com.hackhu.hp.domain.post.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link PostContent} 검사. null은 {@code @NotBlank}가 맡는다. */
public class PostContentValidator implements ConstraintValidator<PostContent, String> {

    static final int MAX_LENGTH = 10_000;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            return false;
        }
        return value.codePoints().noneMatch(PostContentValidator::isForbidden);
    }

    // 줄바꿈과 탭은 본문에 필요하다. NUL(U+0000)은 PostgreSQL TEXT에 저장되지 않아 그대로 두면 500이 난다 (5-1-5).
    private static boolean isForbidden(int codePoint) {
        return codePoint != '\n' && codePoint != '\t' && Character.isISOControl(codePoint);
    }
}
