package com.hackhu.hp.domain.user.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PersonNameValidator implements ConstraintValidator<PersonName, String> {

    // users.name이 VARCHAR(30)이다. PostgreSQL은 코드 포인트로 센다.
    static final int MAX_LENGTH = 30;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (value.codePoints().anyMatch(PersonNameValidator::isForbidden)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("이름에 쓸 수 없는 문자가 있습니다.")
                    .addConstraintViolation();
            return false;
        }
        return value.codePointCount(0, value.length()) <= MAX_LENGTH;
    }

    // 이름은 다른 사람 화면에 작성자로 보인다. 제어 문자와 줄바꿈은 화면을 깨고, 양방향 텍스트 제어 문자는 보이는 글자 순서를
    // 뒤집어 다른 사람의 이름처럼 보이게 할 수 있다 (CWE-451). 양방향 제어 문자는 Unicode의 Bidi_Control 전체를 막는다.
    private static boolean isForbidden(int cp) {
        return Character.getType(cp) == Character.CONTROL
                || cp == ' ' // LINE SEPARATOR
                || cp == ' ' // PARAGRAPH SEPARATOR
                || cp == '؜' // ARABIC LETTER MARK
                || cp == '‎' // LEFT-TO-RIGHT MARK
                || cp == '‏' // RIGHT-TO-LEFT MARK
                || (cp >= '‪' && cp <= '‮') // LRE, RLE, PDF, LRO, RLO
                || (cp >= '⁦' && cp <= '⁩'); // LRI, RLI, FSI, PDI
    }
}
