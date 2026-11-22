package com.hackhu.hp.domain.post.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackhu.hp.TestcontainersConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 게시판이 실제로 작성자 이름을 받는지 ({@link UserAuthorNameReader}). Docker가 필요하다.
 *
 * <p>공지 API 테스트는 {@link AuthorNameReader}를 모킹한다. 다른 팀 도메인을 테스트가 끌어오지 않으려는 것이라 그게 맞지만, 그래서 모킹한
 * 테스트만으로는 <b>구현이 비어 있어도 초록불이 난다.</b> 실제로 이 자리에 빈 맵을 돌려주는 임시 구현이 들어가 있던 동안 공지 API 테스트는 전부 통과했다. 이
 * 테스트가 그 구멍을 막는다.
 *
 * <p>그래서 모킹하지 않고 스프링이 꽂아 준 구현을 그대로 부른다. 받는 타입도 {@link AuthorNameReader}로 두어, 이름을 돌려주지 않는 구현이 다시
 * 들어오면 여기서 깨지게 한다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserAuthorNameReaderTest {

    private static final String HASH = "$2a$12$" + "x".repeat(53);

    @Autowired AuthorNameReader authorNameReader;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM post");
        jdbc.update("DELETE FROM users");
    }

    private long insertActive(String email, String name) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, name, password_hash, role) VALUES (?, ?, ?, 'STAFF')"
                        + " RETURNING id",
                Long.class,
                email,
                name,
                HASH);
    }

    // 탈퇴하면 email, name, password_hash가 함께 NULL이 된다 (ck_users_withdrawn_fields).
    private long insertWithdrawn() {
        return jdbc.queryForObject(
                "INSERT INTO users (role, withdrawn_at) VALUES ('STAFF', now()) RETURNING id",
                Long.class);
    }

    @Test
    void 활동_중인_작성자의_이름을_돌려준다() {
        long id = insertActive("staff@example.com", "홍길동");

        Map<Long, String> names = authorNameReader.findNames(List.of(id));

        assertThat(names).containsExactly(Map.entry(id, "홍길동"));
    }

    @Test
    void 탈퇴한_작성자는_맵에_없다() {
        long active = insertActive("staff@example.com", "홍길동");
        long withdrawn = insertWithdrawn();

        Map<Long, String> names = authorNameReader.findNames(List.of(active, withdrawn));

        assertThat(names).containsOnlyKeys(active);
    }

    @Test
    void 없는_ID는_맵에_없다() {
        long id = insertActive("staff@example.com", "홍길동");

        Map<Long, String> names = authorNameReader.findNames(List.of(id, id + 9999));

        assertThat(names).containsOnlyKeys(id);
    }

    @Test
    void 비어_있으면_빈_맵이다() {
        assertThat(authorNameReader.findNames(List.of())).isEmpty();
    }
}
