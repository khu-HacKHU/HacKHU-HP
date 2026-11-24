package com.hackhu.hp.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hackhu.hp.domain.user.dto.SignupRequest;
import com.hackhu.hp.domain.user.exception.UserErrorCode;
import com.hackhu.hp.domain.user.repository.UserRepository;
import com.hackhu.hp.global.error.BusinessException;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

class SignupServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final SignupService signupService = new SignupService(userRepository, passwordEncoder);

    private final SignupRequest request =
            new SignupRequest("newbie@example.com", "correct-horse-battery", "홍길동");

    private static DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException(
                "insert failed",
                new ConstraintViolationException("insert failed", new SQLException(), constraint));
    }

    @Test
    void 이미_쓰는_이메일이면_해시를_계산하지_않고_409() {
        given(userRepository.existsByEmail("newbie@example.com")).willReturn(true);

        assertThatThrownBy(() -> signupService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(UserErrorCode.AUTH_DUPLICATE_EMAIL);
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void 이메일이_아닌_다른_제약_위반은_409로_바꾸지_않는다() {
        given(passwordEncoder.encode(any())).willReturn("hash");
        given(userRepository.saveAndFlush(any())).willThrow(violation("ck_users_active_fields"));

        // 409로 감추면 진짜 버그가 "이미 가입된 이메일"로 보인다. 500으로 두고 로그로 드러낸다.
        assertThatThrownBy(() -> signupService.signup(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
