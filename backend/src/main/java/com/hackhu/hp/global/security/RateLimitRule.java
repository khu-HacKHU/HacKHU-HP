package com.hackhu.hp.global.security;

import java.time.Duration;
import java.util.Objects;

/**
 * 요청 횟수 제한 규칙 하나. 창({@code window}) 안에서 {@code limit}번까지 허용한다.
 *
 * @param name 카운터를 나누는 이름. 규칙마다 달라야 한다
 * @param limit 창 안에서 허용하는 횟수
 * @param window 창의 길이. 첫 기록부터 이 시간이 지나면 0부터 다시 센다
 */
public record RateLimitRule(String name, int limit, Duration window) {

    public RateLimitRule {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(window, "window");
        if (limit < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("limit은 1 이상, window는 0보다 길어야 합니다.");
        }
    }
}
