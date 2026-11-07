package com.hackhu.hp.domain.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 가입 입력 규칙 (5-1-6 비밀번호, 5-1-8 입력 검증, 4-2-3 필드 규칙). */
class SignupRequestValidationTest {

    private static final String EMAIL = "newbie@example.com";
    private static final String PASSWORD = "correct-horse-battery";
    private static final String NAME = "홍길동";

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static List<String> invalidFields(SignupRequest request) {
        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);
        return violations.stream().map(v -> v.getPropertyPath().toString()).sorted().toList();
    }

    @Test
    void 규칙에_맞으면_통과한다() {
        assertThat(invalidFields(new SignupRequest(EMAIL, PASSWORD, NAME))).isEmpty();
    }

    @Test
    void 필수_항목이_비면_칸마다_하나씩_사유가_나온다() {
        assertThat(invalidFields(new SignupRequest(null, null, null)))
                .containsExactly("email", "name", "password");
        assertThat(invalidFields(new SignupRequest("  ", "", " \t ")))
                .containsExactly("email", "name", "password");
    }

    @Nested
    class 이메일 {

        @Test
        void 앞뒤_공백을_지우고_소문자로_바꾼다() {
            SignupRequest request = new SignupRequest("  NewBie@Example.COM ", PASSWORD, NAME);

            assertThat(request.email()).isEqualTo("newbie@example.com");
            assertThat(invalidFields(request)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"newbie", "newbie@", "@example.com", "new bie@example.com"})
        void 형식이_틀리면_거부한다(String email) {
            assertThat(invalidFields(new SignupRequest(email, PASSWORD, NAME)))
                    .containsOnly("email");
        }

        // 6-RELEASE-TEST-CASES.md I1
        @ParameterizedTest
        @ValueSource(strings = {"홍길동@example.com", "newbie@exämple.com", "\u212Aim@example.com"})
        void ASCII가_아닌_문자가_있으면_거부한다(String email) {
            SignupRequest request = new SignupRequest(email, PASSWORD, NAME);

            assertThat(invalidFields(request)).containsOnly("email");
        }

        @Test
        void 켈빈_기호를_소문자_k로_바꿔_통과시키지_않는다() {
            // "\u212A".toLowerCase(Locale.ROOT)는 ASCII "k"다. 바꾼 뒤 검사하면 통과해 버린다.
            SignupRequest request = new SignupRequest("\u212Aim@example.com", PASSWORD, NAME);

            assertThat(request.email()).isEqualTo("\u212Aim@example.com");
        }

        @Test
        void _254자까지_받는다() {
            String email = "a".repeat(64) + "@" + domainOfLength(254 - 65);

            assertThat(email).hasSize(254);
            assertThat(invalidFields(new SignupRequest(email, PASSWORD, NAME))).isEmpty();
        }

        @Test
        void _255자면_거부한다() {
            String email = "a".repeat(64) + "@" + domainOfLength(255 - 65);

            assertThat(email).hasSize(255);
            assertThat(invalidFields(new SignupRequest(email, PASSWORD, NAME)))
                    .containsOnly("email");
        }

        // 라벨 63자 제한을 지키며 길이를 맞춘 도메인
        private static String domainOfLength(int length) {
            StringBuilder domain = new StringBuilder();
            while (domain.length() < length) {
                int remaining = length - domain.length();
                if (domain.length() > 0) {
                    domain.append('.');
                    remaining--;
                }
                domain.append("b".repeat(Math.min(remaining, 63)));
            }
            return domain.toString();
        }
    }

    @Nested
    class 비밀번호 {

        @Test
        void _10자부터_받는다() {
            assertThat(invalidFields(new SignupRequest(EMAIL, "a".repeat(10), NAME))).isEmpty();
            assertThat(invalidFields(new SignupRequest(EMAIL, "a".repeat(9), NAME)))
                    .containsExactly("password");
        }

        @Test
        void _64자까지_받는다() {
            assertThat(invalidFields(new SignupRequest(EMAIL, "a".repeat(64), NAME))).isEmpty();
            assertThat(invalidFields(new SignupRequest(EMAIL, "a".repeat(65), NAME)))
                    .containsExactly("password");
        }

        @Test
        void 글자_수는_코드_포인트로_센다() {
            // 이모지 5개는 String.length()로 10이지만 5자다. 4바이트씩 20바이트.
            String fiveEmoji = "😀".repeat(5);

            assertThat(invalidFields(new SignupRequest(EMAIL, fiveEmoji, NAME)))
                    .containsExactly("password");
        }

        @Test
        void _64자_이하여도_UTF8로_72바이트를_넘으면_거부한다() {
            // 한글 25자는 75바이트다. bcrypt는 72바이트 뒤를 버린다.
            assertThat(invalidFields(new SignupRequest(EMAIL, "가".repeat(24), NAME))).isEmpty();
            assertThat(invalidFields(new SignupRequest(EMAIL, "가".repeat(25), NAME)))
                    .containsExactly("password");
        }

        @Test
        void 조합_규칙은_없다() {
            assertThat(invalidFields(new SignupRequest(EMAIL, "aaaaaaaaaaaa", NAME))).isEmpty();
        }

        @Test
        void 앞뒤_공백을_지우지_않는다() {
            SignupRequest request = new SignupRequest(EMAIL, "  " + PASSWORD + "  ", NAME);

            assertThat(request.password()).isEqualTo("  " + PASSWORD + "  ");
        }

        @Test
        void 이메일과_같으면_대소문자와_상관없이_거부한다() {
            assertThat(invalidFields(new SignupRequest(EMAIL, "NewBie@Example.com", NAME)))
                    .containsExactly("password");
        }
    }

    @Nested
    class 이름 {

        @Test
        void 앞뒤_공백을_지운다() {
            SignupRequest request = new SignupRequest(EMAIL, PASSWORD, "  홍길동 ");

            assertThat(request.name()).isEqualTo("홍길동");
        }

        @Test
        void 앞뒤_공백을_지운_뒤_30자까지_받는다() {
            assertThat(
                            invalidFields(
                                    new SignupRequest(EMAIL, PASSWORD, " " + "가".repeat(30) + " ")))
                    .isEmpty();
            assertThat(invalidFields(new SignupRequest(EMAIL, PASSWORD, "가".repeat(31))))
                    .containsExactly("name");
        }

        @Test
        void 글자_수는_코드_포인트로_센다() {
            assertThat(invalidFields(new SignupRequest(EMAIL, PASSWORD, "😀".repeat(30))))
                    .isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"홍\u0000길동", "홍\n길동", "홍\t길동", "홍\u007F길동", "홍\u2028길동"})
        void 제어_문자가_있으면_거부한다(String name) {
            assertThat(invalidFields(new SignupRequest(EMAIL, PASSWORD, name)))
                    .containsExactly("name");
        }

        // 6-RELEASE-TEST-CASES.md I2
        @ParameterizedTest
        @ValueSource(
                strings = {
                    "\u202E동길홍",
                    "홍\u202A길동",
                    "홍\u202D길동",
                    "홍\u2066길동",
                    "홍\u2069길동",
                    "홍\u200F길동",
                    "홍\u061C길동"
                })
        void 양방향_텍스트_제어_문자가_있으면_거부한다(String name) {
            assertThat(invalidFields(new SignupRequest(EMAIL, PASSWORD, name)))
                    .containsExactly("name");
        }
    }

    @Test
    void toString에_비밀번호와_개인정보가_나오지_않는다() {
        String text = new SignupRequest(EMAIL, PASSWORD, NAME).toString();

        assertThat(text).doesNotContain(PASSWORD).doesNotContain(EMAIL).doesNotContain(NAME);
    }
}
