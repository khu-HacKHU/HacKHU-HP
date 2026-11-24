package com.hackhu.hp.domain.user.entity;

import java.util.Locale;

/** 이메일 정규화 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md 5-1-8). 가입과 로그인이 같은 규칙을 쓴다. */
public final class Emails {

    private Emails() {}

    /**
     * 앞뒤 공백을 지우고 ASCII 이메일만 소문자로 바꾼다.
     *
     * <p>ASCII가 아닌 문자가 있으면 소문자로 바꾸지 않고 그대로 돌려준다. 검증이 ASCII가 아니라는 이유로 거부하게 하려는 것이다. 먼저 소문자로 바꾸면 켈빈
     * 기호(U+212A)가 {@code k}가 되는 것처럼 ASCII 밖의 문자가 ASCII로 바뀌어 검증을 통과한다. 이런 문자는 Java와 PostgreSQL의 소문자
     * 변환 결과가 달라 {@code ck_users_email_lower} 위반이나 중복 가입을 만들 수 있다.
     *
     * @return {@code raw}가 null이면 null
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String stripped = raw.strip();
        return isAscii(stripped) ? stripped.toLowerCase(Locale.ROOT) : stripped;
    }

    private static boolean isAscii(String value) {
        return value.chars().allMatch(c -> c < 0x80);
    }
}
