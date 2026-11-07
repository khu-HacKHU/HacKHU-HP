package com.hackhu.hp.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hackhu.hp.TestcontainersConfiguration;
import com.hackhu.hp.domain.user.entity.User;
import com.hackhu.hp.domain.user.repository.UserRepository;
import com.hackhu.hp.global.auth.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 가입을 실제 DB로 확인한다. Docker가 필요하다. */
@ExtendWith(OutputCaptureExtension.class)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SignupIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired MockMvc mockMvc;
    @MockitoSpyBean UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        Mockito.reset(userRepository);
        userRepository.deleteAllInBatch();
    }

    private ResultActions signup(String email, String name) throws Exception {
        return signupWithBody(
                """
                {"email": "%s", "password": "%s", "name": "%s"}
                """
                        .formatted(email, PASSWORD, name));
    }

    private ResultActions signupWithBody(String body) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void 가입하면_승인_대기_회원이_되고_비밀번호는_해시로만_저장된다() throws Exception {
        signup("  NewBie@Example.COM ", "  홍길동 ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newbie@example.com"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.role").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        List<User> users = userRepository.findAll();
        assertThat(users).hasSize(1);
        User user = users.getFirst();
        assertThat(user.getEmail()).isEqualTo("newbie@example.com");
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getRole()).isEqualTo(Role.PENDING);
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getApprovedAt()).isNull();
        assertThat(user.getCreatedAt()).isNotNull();
        // bcrypt cost 12 (5-1-6). 원문은 어디에도 없다.
        assertThat(user.getPasswordHash()).startsWith("$2a$12$").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).isTrue();
    }

    @Test
    void 이미_가입된_이메일이면_대소문자가_달라도_409() throws Exception {
        signup("newbie@example.com", "홍길동").andExpect(status().isCreated());

        signup("NEWBIE@example.com", "김철수")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_DUPLICATE_EMAIL"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void 필수_항목이_비면_가입되지_않는다() throws Exception {
        signupWithBody("{\"email\": \"newbie@example.com\", \"password\": \"\", \"name\": \"홍길동\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("password"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void 본문에_role을_넣어도_승인_대기_회원이_된다() throws Exception {
        signupWithBody(
                        """
                        {"email": "newbie@example.com", "password": "%s", "name": "홍길동",
                         "role": "ADMIN", "approvedAt": "2026-11-05T09:30:00Z"}
                        """
                                .formatted(PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PENDING"));

        User user = userRepository.findAll().getFirst();
        assertThat(user.getRole()).isEqualTo(Role.PENDING);
        assertThat(user.getApprovedAt()).isNull();
    }

    @Test
    void 확인과_저장_사이에_같은_이메일이_먼저_저장되어도_500이_아니라_409(CapturedOutput output) throws Exception {
        signup("newbie@example.com", "홍길동").andExpect(status().isCreated());
        // 사전 확인을 통과한 것처럼 만들어 DB 유니크 인덱스(uk_users_email)가 막는 경로를 밟는다.
        doReturn(false).when(userRepository).existsByEmail(anyString());

        signup("newbie@example.com", "김철수")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_DUPLICATE_EMAIL"));

        // 사전 확인이 아니라 저장 시도에서 막혔는지 확인한다. 두 요청 모두 INSERT까지 갔다.
        verify(userRepository, times(2)).saveAndFlush(any(User.class));
        assertThat(userRepository.count()).isEqualTo(1);
        // 유니크 위반의 Detail에 이메일이 들어 있다. 로그에 남지 않아야 한다 (5-1-9, CWE-532).
        assertThat(output).contains("uk_users_email").doesNotContain("newbie@example.com");
    }

    // 6-RELEASE-TEST-CASES.md CC10
    @Test
    void 같은_이메일로_동시에_가입하면_하나만_201이고_나머지는_409() throws Exception {
        int requests = 4;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                String name = "가입자" + i;
                results.add(
                        executor.submit(
                                () -> {
                                    start.await();
                                    return signup("race@example.com", name)
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            }
            start.countDown();
        }

        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        assertThat(statuses).containsOnly(201, 409);
        assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
        assertThat(userRepository.count()).isEqualTo(1);
    }
}
