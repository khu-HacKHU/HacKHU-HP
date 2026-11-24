package com.hackhu.hp.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * 테스트에서 시간을 옮길 수 있는 시계. 처음에는 실제 시각에서 시작한다.
 *
 * <p>운영 시계({@code ClockConfig})처럼 마이크로초 단위로 끊는다. DB가 마이크로초까지만 저장하기 때문이다.
 */
public final class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock() {
        this(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    public MutableClock(Instant start) {
        this.now = start;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    /** 실제 시각으로 되돌린다. 테스트마다 처음에 부른다. */
    public void reset() {
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }
}
