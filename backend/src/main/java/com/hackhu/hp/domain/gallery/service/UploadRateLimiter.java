package com.hackhu.hp.domain.gallery.service;

import com.hackhu.hp.domain.gallery.exception.GalleryErrorCode;
import com.hackhu.hp.global.error.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

/**
 * 업로드 주소 발급 횟수 제한. 사용자당 분당 5회 호출, 시간당 발급 파일 100장 (docs/spec/gallery/backend/5-1 4절).
 *
 * <p>인스턴스가 하나뿐이라(DECISIONS D3) in-memory 카운터로 충분하다. 여러 인스턴스로 늘리면 Redis 같은 공용 저장소로 바꿔야 한다.
 */
@Component
public class UploadRateLimiter {

    private static final int MAX_CALLS_PER_MINUTE = 5;
    private static final int MAX_FILES_PER_HOUR = 100;
    private static final Duration CALL_WINDOW = Duration.ofMinutes(1);
    private static final Duration FILE_WINDOW = Duration.ofHours(1);

    private final Map<Long, Deque<Instant>> callLog = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Instant>> fileLog = new ConcurrentHashMap<>();

    /** 한도를 넘으면 429로 거부하고, 넘지 않으면 이번 요청을 기록한다. */
    public synchronized void checkAndRecord(Long userId, int fileCount) {
        Instant now = Instant.now();

        Deque<Instant> calls =
                callLog.computeIfAbsent(userId, key -> new ConcurrentLinkedDeque<>());
        Deque<Instant> files =
                fileLog.computeIfAbsent(userId, key -> new ConcurrentLinkedDeque<>());
        prune(calls, now.minus(CALL_WINDOW));
        prune(files, now.minus(FILE_WINDOW));

        if (calls.size() >= MAX_CALLS_PER_MINUTE || files.size() + fileCount > MAX_FILES_PER_HOUR) {
            throw new BusinessException(GalleryErrorCode.TOO_MANY_REQUESTS);
        }

        calls.addLast(now);
        for (int i = 0; i < fileCount; i++) {
            files.addLast(now);
        }
    }

    private void prune(Deque<Instant> log, Instant cutoff) {
        while (!log.isEmpty() && log.peekFirst().isBefore(cutoff)) {
            log.pollFirst();
        }
    }
}
