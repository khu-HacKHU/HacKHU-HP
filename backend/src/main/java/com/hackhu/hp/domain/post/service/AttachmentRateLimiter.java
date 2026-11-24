package com.hackhu.hp.domain.post.service;

import com.hackhu.hp.global.error.TooManyRequestsException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 자료게시판 첨부 요청 횟수 제한 (2-REQUIREMENTS.md 2-5).
 *
 * <ul>
 *   <li>업로드 주소 발급: 사용자당 1분에 5번, 1시간에 파일 50개
 *   <li>다운로드 주소 발급: 사용자당 1분에 30번
 * </ul>
 *
 * <p>갤러리 {@code UploadRateLimiter}와 같은 방식이다. 인스턴스가 하나뿐이라 메모리 카운터로 충분하다 (DECISIONS D3).
 */
@Slf4j
@Component
class AttachmentRateLimiter {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final int UPLOAD_CALLS_PER_MINUTE = 5;
    private static final int UPLOAD_FILES_PER_HOUR = 50;
    private static final int DOWNLOAD_CALLS_PER_MINUTE = 30;

    private final Map<Long, Deque<Instant>> uploadCalls = new HashMap<>();
    private final Map<Long, Deque<Instant>> uploadFiles = new HashMap<>();
    private final Map<Long, Deque<Instant>> downloadCalls = new HashMap<>();

    synchronized void checkUpload(Long userId, int fileCount) {
        Instant now = Instant.now();
        Deque<Instant> calls = window(uploadCalls, userId, now.minus(MINUTE));
        Deque<Instant> files = window(uploadFiles, userId, now.minus(HOUR));
        if (calls.size() >= UPLOAD_CALLS_PER_MINUTE) {
            reject(userId, "첨부 업로드 주소 발급", calls.peekFirst().plus(MINUTE), now);
        }
        if (files.size() + fileCount > UPLOAD_FILES_PER_HOUR) {
            reject(userId, "첨부 업로드 주소 발급", files.peekFirst().plus(HOUR), now);
        }
        calls.addLast(now);
        for (int i = 0; i < fileCount; i++) {
            files.addLast(now);
        }
    }

    synchronized void checkDownload(Long userId) {
        Instant now = Instant.now();
        Deque<Instant> calls = window(downloadCalls, userId, now.minus(MINUTE));
        if (calls.size() >= DOWNLOAD_CALLS_PER_MINUTE) {
            reject(userId, "첨부 다운로드 주소 발급", calls.peekFirst().plus(MINUTE), now);
        }
        calls.addLast(now);
    }

    private static Deque<Instant> window(
            Map<Long, Deque<Instant>> log, Long userId, Instant cutoff) {
        Deque<Instant> entries = log.computeIfAbsent(userId, key -> new ArrayDeque<>());
        while (!entries.isEmpty() && entries.peekFirst().isBefore(cutoff)) {
            entries.pollFirst();
        }
        return entries;
    }

    /** 요청 횟수 제한에 걸린 기록 (5-1-12). */
    private static void reject(Long userId, String api, Instant availableAt, Instant now) {
        log.info("요청 횟수 제한 걸림 userId={} api={}", userId, api);
        Duration retryAfter = Duration.between(now, availableAt);
        throw new TooManyRequestsException(retryAfter.isNegative() ? Duration.ZERO : retryAfter);
    }
}
