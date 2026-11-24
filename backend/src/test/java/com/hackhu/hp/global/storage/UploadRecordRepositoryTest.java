package com.hackhu.hp.global.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hackhu.hp.TestcontainersConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 조건부 갱신이 실제 PostgreSQL에서 동작하는지 확인한다.
 *
 * <p>서비스 단위 테스트는 "갱신 수가 요청 수와 다르면 거부한다"는 판단만 본다. 그 수가 올바르게 나오는지, 즉 쿼리의 조건이 맞는지는 진짜 DB에서만 확인된다. 같은
 * {@code uploadId}를 두 요청이 확정하려 할 때 하나만 성공해야 한다 (board 5-1-8, CWE-362).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UploadRecordRepositoryTest {

    private static final Long OWNER = 7L;
    private static final Long OTHER_USER = 99L;

    @Autowired UploadRecordRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
    }

    private UploadRecord saveIssued(Long userId, Instant expiresAt) {
        return repository.save(
                UploadRecord.issue(userId, UploadPurpose.GALLERY, "image/jpeg", 1024, expiresAt));
    }

    private UploadRecord saveIssued(Long userId) {
        return saveIssued(userId, Instant.now().plus(10, ChronoUnit.MINUTES));
    }

    @Test
    void 처음_확정하면_갱신된다() {
        UploadRecord record = saveIssued(OWNER);

        int claimed = repository.claim(List.of(record.getId()), OWNER, Instant.now());

        assertEquals(1, claimed);
        assertFalse(repository.findById(record.getId()).orElseThrow().isPending());
    }

    @Test
    void 같은_업로드를_두_번_확정하면_두_번째는_갱신되지_않는다() {
        UploadRecord record = saveIssued(OWNER);

        int first = repository.claim(List.of(record.getId()), OWNER, Instant.now());
        int second = repository.claim(List.of(record.getId()), OWNER, Instant.now());

        // 두 요청이 동시에 와도 하나만 성공한다. 읽고 나서 쓰는 방식이면 둘 다 통과한다.
        assertEquals(1, first);
        assertEquals(0, second);
    }

    @Test
    void 남의_업로드는_갱신되지_않는다() {
        UploadRecord record = saveIssued(OTHER_USER);

        int claimed = repository.claim(List.of(record.getId()), OWNER, Instant.now());

        assertEquals(0, claimed);
        assertTrue(repository.findById(record.getId()).orElseThrow().isPending());
    }

    @Test
    void 만료된_업로드는_갱신되지_않는다() {
        UploadRecord record = saveIssued(OWNER, Instant.now().minus(1, ChronoUnit.MINUTES));

        int claimed = repository.claim(List.of(record.getId()), OWNER, Instant.now());

        assertEquals(0, claimed);
    }

    @Test
    void 여러_장_중_하나라도_못_가져오면_갱신_수가_요청_수보다_적다() {
        UploadRecord mine = saveIssued(OWNER);
        UploadRecord taken = saveIssued(OWNER);
        repository.claim(List.of(taken.getId()), OWNER, Instant.now());

        int claimed = repository.claim(List.of(mine.getId(), taken.getId()), OWNER, Instant.now());

        // 호출하는 쪽이 이 수를 보고 전체를 거부한다.
        assertEquals(1, claimed);
    }

    @Test
    void 없는_업로드_ID는_갱신_수에_들어가지_않는다() {
        int claimed = repository.claim(List.of(UUID.randomUUID()), OWNER, Instant.now());

        assertEquals(0, claimed);
    }
}
