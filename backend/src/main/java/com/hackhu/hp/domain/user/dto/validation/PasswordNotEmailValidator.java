package com.hackhu.hp.domain.user.dto.validation;

import com.hackhu.hp.domain.user.dto.SignupRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordNotEmailValidator
        implements ConstraintValidator<PasswordNotEmail, SignupRequest> {

    @Override
    public boolean isValid(SignupRequest request, ConstraintValidatorContext context) {
        // 비었으면 @NotBlank가 사유를 낸다. 둘 다 빈 문자열일 때 "이메일과 같음"까지 겹쳐 나가지 않게 한다.
        if (request == null || isBlank(request.email()) || isBlank(request.password())) {
            return true;
        }
        if (!request.password().equalsIgnoreCase(request.email())) {
            return true;
        }
        // 클래스 단위 검사지만 프론트엔드가 칸별로 표시하도록 password 칸의 사유로 낸다.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("password")
                .addConstraintViolation();
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
