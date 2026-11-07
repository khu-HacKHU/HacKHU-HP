package com.hackhu.hp.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hackhu.hp.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** V1 마이그레이션의 제약과 트리거 (3-2-DESIGN-DB-SCHEMA.md 3-2-2). Docker가 필요하다. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UsersSchemaTest {

    private static final String HASH = "$2a$12$" + "x".repeat(53);

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM users");
    }

    private long insertActive(String email) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, name, password_hash, role) VALUES (?, ?, ?, 'PENDING')"
                        + " RETURNING id",
                Long.class,
                email,
                "홍길동",
                HASH);
    }

    @Test
    void 대문자가_섞인_이메일은_저장되지_않는다() {
        assertThatThrownBy(() -> insertActive("NewBie@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_email_lower");
    }

    @Test
    void 활성_계정끼리_이메일이_겹치지_않는다() {
        insertActive("newbie@example.com");

        assertThatThrownBy(() -> insertActive("newbie@example.com"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_users_email");
    }

    @Test
    void 탈퇴한_계정의_이메일로_다시_가입할_수_있다() {
        long id = insertActive("newbie@example.com");
        jdbc.update(
                "UPDATE users SET email = NULL, name = NULL, password_hash = NULL,"
                        + " withdrawn_at = now() WHERE id = ?",
                id);

        insertActive("newbie@example.com");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(2);
    }

    @Test
    void 정의되지_않은_역할은_저장되지_않는다() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO users (email, name, password_hash, role)"
                                                + " VALUES ('a@example.com', '홍길동', ?, 'ROOT')",
                                        HASH))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_role");
    }

    @Test
    void 활성_계정은_로그인_정보가_비지_않는다() {
        assertThatThrownBy(
                        () ->
                                jdbc.update(
                                        "INSERT INTO users (email, name, password_hash, role)"
                                                + " VALUES ('a@example.com', '홍길동', NULL, 'PENDING')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_active_fields");
    }

    @Test
    void 탈퇴한_계정에_개인정보가_남지_않는다() {
        long id = insertActive("newbie@example.com");

        assertThatThrownBy(
                        () -> jdbc.update("UPDATE users SET withdrawn_at = now() WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_withdrawn_fields");
    }

    @Test
    void 행이_바뀌면_updated_at이_갱신된다() {
        long id = insertActive("newbie@example.com");
        // 같은 트랜잭션 안의 now()는 같은 값이라 비교가 되도록 시각을 과거로 돌려 둔다.
        jdbc.update("UPDATE users SET updated_at = '2000-01-01T00:00:00Z' WHERE id = ?", id);

        jdbc.update("UPDATE users SET role = 'MEMBER' WHERE id = ?", id);

        Instant updatedAt =
                jdbc.queryForObject("SELECT updated_at FROM users WHERE id = ?", Instant.class, id);
        assertThat(updatedAt).isAfter(Instant.parse("2020-01-01T00:00:00Z"));
    }
}
