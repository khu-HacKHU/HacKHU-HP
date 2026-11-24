package com.hackhu.hp.domain.user.entity;

import com.hackhu.hp.global.auth.Role;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * 회원 (docs/spec/auth/backend/3-2-DESIGN-DB-SCHEMA.md 3-2-2).
 *
 * <p>탈퇴하면 행을 지우지 않고 {@code email}, {@code name}, {@code passwordHash}를 비운다. 다른 기능의 글과 앨범이 이 행을 참조하기
 * 때문이다 (DECISIONS.md A7).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String name;

    private String passwordHash;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean mustChangePassword;

    private Instant tempPasswordExpiresAt;

    private Instant tempPasswordUsedAt;

    private Instant approvedAt;

    private Instant withdrawnAt;

    // 시각은 DB의 now()가 정한다. 서버 시계와 DB 시계가 섞이지 않게 저장 뒤 DB 값을 다시 읽는다.
    @Generated private Instant createdAt;

    // 행이 바뀔 때마다 트리거가 갱신한다 (V1__create_users.sql).
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private Instant updatedAt;

    /**
     * 가입한 사람. 승인 대기 회원({@link Role#PENDING})으로 시작한다 (USER-STORIES.md 1절).
     *
     * @param email {@link Emails#normalize}로 정규화한 이메일
     * @param name 앞뒤 공백을 지운 이름
     * @param passwordHash bcrypt 해시. 비밀번호 원문을 넘기지 않는다
     */
    public static User signUp(String email, String name, String passwordHash) {
        User user = new User();
        user.email = email;
        user.name = name;
        user.passwordHash = passwordHash;
        user.role = Role.PENDING;
        user.mustChangePassword = false;
        return user;
    }
}
