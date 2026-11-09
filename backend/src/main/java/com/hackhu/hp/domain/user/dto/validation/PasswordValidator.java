package com.hackhu.hp.domain.user.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class PasswordValidator implements ConstraintValidator<Password, String> {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 64;

    // bcrypt는 72바이트 뒤를 버린다. 넘으면 뒷부분이 달라도 같은 비밀번호로 통과한다.
    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // 비었는지는 @NotBlank가 본다. 여기서도 거부하면 같은 칸에 사유가 두 개 나간다.
        if (value == null || value.isBlank()) {
            return true;
        }
        // 글자 수는 코드 포인트로 센다. String.length()는 이모지 하나를 2로 센다.
        int length = value.codePointCount(0, value.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                            "비밀번호가 너무 깁니다. 한글이나 기호가 많으면 64자보다 짧아도 길이 제한을 넘을 수 있습니다.")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
