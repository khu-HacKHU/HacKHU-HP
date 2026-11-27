package com.hackhu.hp.domain.user.service;

import com.hackhu.hp.domain.user.dto.SignupRequest;
import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.domain.user.repository.UserRepository;
import com.hackhu.hp.global.auth.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 처음 관리자 생성 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-6, DECISIONS.md A10).
 *
 * <p>서버가 요청을 받기 전에, 활성 관리자가 0명이면 {@link InitialAdminProperties}의 값으로 관리자 한 명을 만든다. 활성 관리자가 한 명이라도
 * 있으면 아무것도 하지 않는다. 두 번째 관리자부터는 첫 관리자가 역할 변경으로 지정한다.
 *
 * <p>"이 이메일로 가입하면 관리자가 된다"는 방식은 쓰지 않는다. 메일 인증이 없어 다른 사람이 그 이메일로 먼저 가입하면 관리자 자리를 가져간다. 그래서 같은 이메일의
 * 활성 계정이 이미 있으면 그 계정을 올리지 않고 서버를 멈춘다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(InitialAdminProperties.class)
public class InitialAdminService implements SmartInitializingSingleton {

    /**
     * 처음 관리자 생성을 서버끼리 직렬화하는 advisory lock의 키 (5-2-QUALITY-CONCURRENCY.md 5-2-2). 다른 기능이 advisory
     * lock을 쓸 때 이 값과 겹치지 않게 한다. 값은 "HACKHU"의 ASCII 코드 뒤에 01을 붙인 것이다.
     */
    static final long LOCK_KEY = 0x4841434B485501L;

    // 검증 오류를 어느 환경변수의 문제인지로 보여 준다. 값 자체는 싣지 않는다 (CWE-532).
    private static final Map<String, String> ENV_NAMES =
            Map.of(
                    "email", "INITIAL_ADMIN_EMAIL",
                    "name", "INITIAL_ADMIN_NAME",
                    "password", "INITIAL_ADMIN_PASSWORD");

    private final InitialAdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    /** 처음 관리자를 만들었는지, 만들지 않았다면 왜인지. */
    public enum Outcome {
        CREATED,
        ADMIN_EXISTS,
        NOT_CONFIGURED
    }

    /**
     * 모든 빈을 만든 뒤, 웹 서버가 요청을 받기 전에 부른다. Flyway 마이그레이션은 이미 끝났다. 여기서 예외가 나면 서버가 시작하지 않는다.
     *
     * <p>{@code ApplicationRunner}는 웹 서버가 열린 뒤에 돌아서, 그 사이 들어온 가입이 관리자 이메일을 먼저 차지할 수 있다.
     */
    @Override
    public void afterSingletonsInstantiated() {
        createIfNoActiveAdmin(properties);
    }

    /**
     * 활성 관리자가 0명이면 {@code values}로 관리자를 만든다.
     *
     * <p>advisory lock은 트랜잭션이 끝나면 풀린다. 서버 여러 대가 동시에 시작해도 한 대만 만들고, 나머지는 잠금을 기다린 뒤 관리자가 있음을 보고 건너뛴다
     * (6-RELEASE-TEST-CASES.md CC9). bcrypt 계산(약 0.3초)을 잠금 안에서 하지만 서버 시작 때 한 번뿐이라 감수한다.
     *
     * @throws IllegalStateException 활성 관리자가 0명인데 값이 없고 {@code required}가 켜져 있을 때, 값이 일부만 있거나 규칙에 맞지
     *     않을 때, 같은 이메일의 활성 계정이 이미 있을 때
     */
    public Outcome createIfNoActiveAdmin(InitialAdminProperties values) {
        return transactions.execute(status -> {
            jdbc.query("SELECT pg_advisory_xact_lock(?)", (RowCallbackHandler) rs -> {}, LOCK_KEY);

            if (userRepository.countByRoleAndWithdrawnAtIsNull(Role.ADMIN) > 0) {
                if (!values.isEmpty()) {
                    log.warn("활성 관리자가 이미 있어 INITIAL_ADMIN_* 값을 쓰지 않았습니다. 시크릿에서 지우세요.");
                }
                return Outcome.ADMIN_EXISTS;
            }

            if (values.isEmpty()) {
                if (values.required()) {
                    throw new IllegalStateException(
                            "활성 관리자가 없는데 INITIAL_ADMIN_EMAIL, INITIAL_ADMIN_NAME, INITIAL_ADMIN_PASSWORD가 없습니다.");
                }
                log.warn("활성 관리자가 없습니다. INITIAL_ADMIN_* 값이 없어 처음 관리자를 만들지 않았습니다.");
                return Outcome.NOT_CONFIGURED;
            }

            // 가입과 같은 규칙으로 정규화하고 검사한다. 값이 일부만 있으면 빈 칸이 @NotBlank에 걸린다.
            SignupRequest request =
                    new SignupRequest(values.email(), values.password(), values.name());
            Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new IllegalStateException("처음 관리자 값이 규칙에 맞지 않습니다. " + describe(violations));
            }

            if (userRepository.existsByEmail(request.email())) {
                throw new IllegalStateException(
                        "INITIAL_ADMIN_EMAIL을 이미 다른 활성 계정이 씁니다. 그 계정을 관리자로 올리지 않습니다.");
            }

            User admin =
                    userRepository.saveAndFlush(
                            User.initialAdmin(
                                    request.email(),
                                    request.name(),
                                    passwordEncoder.encode(request.password())));
            // 이메일과 이름은 로그에 남기지 않는다 (5-1-9, CWE-532).
            log.info("처음 관리자를 만들었습니다: userId={}", admin.getId());
            log.warn("관리자가 첫 로그인에서 비밀번호를 바꾼 뒤 INITIAL_ADMIN_* 시크릿을 지우세요.");
            return Outcome.CREATED;
        });
    }

    private static String describe(Set<ConstraintViolation<SignupRequest>> violations) {
        return violations.stream()
                .map(v -> envName(v) + ": " + v.getMessage())
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(" / "));
    }

    private static String envName(ConstraintViolation<?> violation) {
        String property = violation.getPropertyPath().toString();
        return ENV_NAMES.getOrDefault(property, property);
    }
}
