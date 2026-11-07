package com.hackhu.hp.domain.post.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** {@link PostTitle} 검사. null은 {@code @NotBlank}가 맡는다. */
public class PostTitleValidator implements ConstraintValidator<PostTitle, String> {

    static final int MAX_LENGTH = 100;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        // PostgreSQL char_length와 같게 코드 포인트로 센다. String.length()는 이모지를 두 자로 센다 (3-2-2).
        if (value.codePointCount(0, value.length()) > MAX_LENGTH) {
            return false;
        }
        return value.codePoints().noneMatch(PostTitleValidator::isForbidden);
    }

    // 제목은 목록에서 다른 글과 나란히 보이므로 줄바꿈과 글자 방향을 뒤집는 문자를 받지 않는다 (5-1-5).
    private static boolean isForbidden(int codePoint) {
        return Character.isISOControl(codePoint) || BidiControls.isBidiControl(codePoint);
    }
}
