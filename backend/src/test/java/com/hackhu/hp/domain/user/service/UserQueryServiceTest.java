package com.hackhu.hp.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * 다른 기능에 주는 회원 이름 조회 (#115). Docker가 필요하다.
 *
 * <p>조회 결과가 아니라 <b>무엇을 주지 않는지</b>가 이 메서드의 계약이다. 그래서 돌려주는 타입이 이름만 담는지도 함께 확인한다 (auth 5-1-9).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserQueryServiceTest {

    private static final String HASH = "$2a$12$" + "x".repeat(53);

    @Autowired UserQueryService userQueryService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM users");
    }

    private long insertActive(String email, String name) {
        return jdbc.queryForObject(
                "INSERT INTO users (email, name, password_hash, role) VALUES (?, ?, ?, 'MEMBER')"
                        + " RETURNING id",
                Long.class,
                email,
                name,
                HASH);
    }

    // 탈퇴하면 email, name, password_hash가 함께 NULL이 된다 (ck_users_withdrawn_fields).
    private long insertWithdrawn() {
        return jdbc.queryForObject(
                "INSERT INTO users (email, name, password_hash, role, withdrawn_at)"
                        + " VALUES (NULL, NULL, NULL, 'MEMBER', now()) RETURNING id",
                Long.class);
    }

    @Test
    void 활성_회원의_이름을_돌려준다() {
        long gildong = insertActive("gildong@example.com", "홍길동");
        long younghee = insertActive("younghee@example.com", "김영희");

        assertThat(userQueryService.findDisplayNames(List.of(gildong, younghee)))
                .containsOnly(Map.entry(gildong, "홍길동"), Map.entry(younghee, "김영희"));
    }

    @Test
    void 탈퇴한_회원은_맵에_없다() {
        long active = insertActive("gildong@example.com", "홍길동");
        long withdrawn = insertWithdrawn();

        Map<Long, String> names = userQueryService.findDisplayNames(List.of(active, withdrawn));

        assertThat(names).containsOnlyKeys(active);
        // 값이 null인 항목을 주면 부르는 쪽이 "이름이 비어 있음"과 "탈퇴"를 구분하려다 NPE를 만난다.
        assertThat(names).doesNotContainValue(null);
    }

    @Test
    void 없는_ID는_맵에_없고_탈퇴와_구분되지_않는다() {
        long active = insertActive("gildong@example.com", "홍길동");
        long withdrawn = insertWithdrawn();
        long missing = active + withdrawn + 1000;

        Map<Long, String> names =
                userQueryService.findDisplayNames(List.of(active, withdrawn, missing));

        // 둘 다 "없음"이다. 다르게 주면 ID로 계정이 있었는지를 떠볼 수 있다.
        assertThat(names).containsOnlyKeys(active);
    }

    @Test
    void 빈_목록이면_빈_맵이다() {
        assertThat(userQueryService.findDisplayNames(List.of())).isEmpty();
    }

    @Test
    void null_목록이면_빈_맵이다() {
        assertThat(userQueryService.findDisplayNames(null)).isEmpty();
    }

    @Test
    void 중복된_ID를_넣어도_한_항목이다() {
        long gildong = insertActive("gildong@example.com", "홍길동");

        assertThat(userQueryService.findDisplayNames(List.of(gildong, gildong, gildong)))
                .containsExactly(Map.entry(gildong, "홍길동"));
    }

    @Test
    void 돌려준_맵은_바꿀_수_없다() {
        long gildong = insertActive("gildong@example.com", "홍길동");

        Map<Long, String> names = userQueryService.findDisplayNames(List.of(gildong));

        assertThatThrownBy(() -> names.put(999L, "끼어든 이름"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 이름_말고는_아무것도_주지_않는다() {
        long gildong = insertActive("gildong@example.com", "홍길동");

        Map<Long, String> names = userQueryService.findDisplayNames(List.of(gildong));

        // 반환 타입이 Map<Long, String>이라 이메일, 역할, User 엔티티가 실릴 자리가 없다.
        // 이 테스트는 시그니처가 바뀌어 엔티티나 DTO가 새어 나가면 깨진다 (auth 5-1-9).
        assertThat(names.values()).containsExactly("홍길동");
        assertThat(names.values())
                .allSatisfy(value -> assertThat(value).isInstanceOf(String.class));
        assertThat(names.values().toString()).doesNotContain("gildong@example.com", "MEMBER", HASH);
    }
}
