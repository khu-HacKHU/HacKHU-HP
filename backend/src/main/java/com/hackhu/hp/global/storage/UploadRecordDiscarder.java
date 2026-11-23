package com.hackhu.hp.global.storage;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 확정에 실패한 업로드의 기록을 사용 처리한다. 호출하는 쪽 트랜잭션과 따로 커밋한다.
 *
 * <p>확정 실패 경로는 S3 객체를 지운 뒤 예외를 던진다. 사용 처리가 호출하는 쪽 트랜잭션에 참여하면 그 예외로 롤백되어 기록만 {@code ISSUED}로 돌아가고
 * 객체는 이미 없다. 그러면 같은 {@code uploadId}로 다시 확정할 때 "형식이 아니다"라는 틀린 사유를 만료까지 계속 받는다 (PR #135 리뷰,
 * CWE-459). 그래서 {@code REQUIRES_NEW}로 먼저 커밋해 둔다.
 *
 * <p>{@link UploadConfirmService} 안의 메서드로 두면 자기 호출이라 트랜잭션 프록시를 거치지 않아서 따로 둔다.
 */
@Component
public class UploadRecordDiscarder {

    private final UploadRecordRepository uploadRecordRepository;

    public UploadRecordDiscarder(UploadRecordRepository uploadRecordRepository) {
        this.uploadRecordRepository = uploadRecordRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void claimInNewTransaction(Collection<UUID> ids, Long userId, Instant now) {
        uploadRecordRepository.claim(ids, userId, now);
    }
}
