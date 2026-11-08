package com.hackhu.hp.global.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface UploadRecordRepository extends JpaRepository<UploadRecord, UUID> {

    // 공용 컴포넌트라 호출하는 쪽이 트랜잭션을 걸었는지 가정하지 않는다. 기본 전파가 REQUIRED라서,
    // 앨범 생성처럼 이미 트랜잭션 안이면 그 트랜잭션에 참여하고 아니면 자기 트랜잭션을 연다.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
            """
            update UploadRecord r
               set r.status = :used
             where r.id in :ids
               and r.status = :issued
               and r.userId = :userId
               and r.expiresAt > :now
            """)
    int markUsed(
            Collection<UUID> ids, Long userId, Instant now, UploadStatus issued, UploadStatus used);

    /**
     * 발급 기록을 조건부 갱신으로 사용 처리하고 갱신된 행 수를 돌려준다. 호출하는 쪽은 그 수가 요청 수와 같은지 확인해야 한다 (board 5-1-8, CWE-362).
     *
     * <p>읽고 나서 쓰는 방식이면 같은 {@code uploadId}를 두 요청이 동시에 확정할 때 둘 다 통과한다. 조건을 갱신문 안에 두면 DB가 행을 잠그므로 하나만
     * 갱신에 성공한다.
     */
    default int claim(Collection<UUID> ids, Long userId, Instant now) {
        if (ids.isEmpty()) {
            return 0;
        }
        return markUsed(ids, userId, now, UploadStatus.ISSUED, UploadStatus.USED);
    }
}
