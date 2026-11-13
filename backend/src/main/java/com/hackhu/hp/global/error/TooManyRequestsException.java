package com.hackhu.hp.global.error;

import java.time.Duration;

/**
 * 요청 횟수 제한에 걸림. {@link GlobalExceptionHandler}가 {@code 429 TOO_MANY_REQUESTS}와 {@code Retry-After}
 * 헤더로 바꾼다 (api.md 3절).
 */
public class TooManyRequestsException extends BusinessException {

    private final long retryAfterSeconds;

    /**
     * @param retryAfter 다시 시도할 수 있을 때까지 남은 시간. 1초보다 짧으면 1초로 올린다.
     */
    public TooManyRequestsException(Duration retryAfter) {
        super(CommonErrorCode.TOO_MANY_REQUESTS);
        long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
        this.retryAfterSeconds = Math.max(1, seconds);
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
