package com.hackhu.hp.domain.user.service;

import com.hackhu.hp.domain.user.dto.SignupRequest;
import com.hackhu.hp.domain.user.dto.SignupResponse;
import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.domain.user.exception.UserErrorCode;
import com.hackhu.hp.domain.user.repository.UserRepository;
import com.hackhu.hp.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 가입 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-3). */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    private static final String EMAIL_UNIQUE_INDEX = "uk_users_email";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 승인 대기 회원을 만든다. 로그인시키지 않는다.
     *
     * <p>트랜잭션으로 묶지 않는다. bcrypt 계산(약 0.3초) 동안 DB 연결을 잡고 있지 않으려는 것이다 (DECISIONS.md A15). 쓰기는 행 추가
     * 하나라서 따로 묶을 것이 없다.
     *
     * @throws BusinessException 활성 계정이 이미 이 이메일을 쓰면 {@code AUTH_DUPLICATE_EMAIL}
     */
    public SignupResponse signup(SignupRequest request) {
        // 이미 쓰는 이메일이면 bcrypt를 계산하기 전에 끝낸다. 중복 여부는 409로 어차피 드러난다 (5-1-11).
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.AUTH_DUPLICATE_EMAIL);
        }
        String passwordHash = passwordEncoder.encode(request.password());

        User user;
        try {
            user =
                    userRepository.saveAndFlush(
                            User.signUp(request.email(), request.name(), passwordHash));
        } catch (DataIntegrityViolationException e) {
            // 위의 확인과 저장 사이에 같은 이메일로 먼저 가입한 요청이 있으면 유니크 인덱스가 막는다.
            // 500이 아니라 409로 준다 (5-2-QUALITY-CONCURRENCY.md, CWE-362).
            if (violates(e, EMAIL_UNIQUE_INDEX)) {
                throw new BusinessException(UserErrorCode.AUTH_DUPLICATE_EMAIL);
            }
            throw e;
        }
        // 이메일과 이름은 로그에 남기지 않는다 (5-1-9, CWE-532).
        log.info("가입: userId={}", user.getId());
        return SignupResponse.from(user);
    }

    private static boolean violates(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException cve) {
                return constraint.equalsIgnoreCase(cve.getConstraintName());
            }
        }
        return false;
    }
}
