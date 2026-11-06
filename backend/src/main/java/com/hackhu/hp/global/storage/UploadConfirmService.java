package com.hackhu.hp.global.storage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 발급받은 업로드를 확정한다. 갤러리 앨범과 자료게시판 첨부가 같이 쓴다.
 *
 * <p>검사 순서는 싼 것부터다. 발급 기록 조회(소유자·만료·중복)로 먼저 걸러내고, 통과한 것만 S3에서 실제 바이트를 읽어 매직바이트를 본다
 * (docs/spec/gallery/backend/4-2 4절).
 *
 * <p>트랜잭션을 걸지 않는다. 실패 처리에서 S3 객체를 지우고 기록을 소비 처리해야 하는데, 같은 트랜잭션 안에서 예외를 던지면 기록 변경이 롤백돼 S3와 DB가 어긋난다.
 */
@Service
public class UploadConfirmService {

    private final UploadRecordRepository uploadRecordRepository;
    private final ImageFormatValidator imageFormatValidator;
    private final S3ObjectStore objectStore;

    public UploadConfirmService(
            UploadRecordRepository uploadRecordRepository,
            ImageFormatValidator imageFormatValidator,
            S3ObjectStore objectStore) {
        this.uploadRecordRepository = uploadRecordRepository;
        this.imageFormatValidator = imageFormatValidator;
        this.objectStore = objectStore;
    }

    /**
     * 전부 통과하면 요청 순서대로 object key를 돌려주고, 기록을 USED로 바꾼다. 하나라도 실패하면 이번 요청에서 아직 안 쓰인 객체를 모두 지우고 그 기록들을
     * 소비 처리한 뒤 {@link UploadConfirmException}을 던진다.
     *
     * <p>ponytail: 같은 uploadId로 동시에 두 번 확정하면 둘 다 통과할 수 있다. 사진 하나가 두 앨범에 들어가는 정도의 영향이라 지금은 둔다. 문제가
     * 되면 기록에 낙관적 락(@Version)을 붙인다.
     */
    public List<String> confirmImages(Long userId, UploadPurpose purpose, List<UUID> uploadIds) {
        Map<UUID, UploadRecord> found =
                uploadRecordRepository.findAllById(uploadIds).stream()
                        .collect(Collectors.toMap(UploadRecord::getId, Function.identity()));

        Instant now = Instant.now();
        List<UploadConfirmException.Failure> failures = new ArrayList<>();
        List<UploadRecord> cleanupTargets = new ArrayList<>();
        UploadRecord[] usable = new UploadRecord[uploadIds.size()];
        Set<UUID> seen = new HashSet<>();

        for (int i = 0; i < uploadIds.size(); i++) {
            UUID uploadId = uploadIds.get(i);
            if (!seen.add(uploadId)) {
                failures.add(fail(i, uploadId, "같은 사진을 두 번 넣을 수 없습니다."));
                continue;
            }

            UploadRecord record = found.get(uploadId);
            if (record == null || !record.isOwnedBy(userId) || record.getPurpose() != purpose) {
                failures.add(fail(i, uploadId, "본인이 발급받은 업로드 주소가 아닙니다."));
                continue;
            }

            // 아직 어디에도 안 붙은 객체만 정리 대상이다. 이미 USED인 객체를 지우면 살아있는 앨범의 사진이 사라진다.
            if (record.isPending()) {
                cleanupTargets.add(record);
            }
            if (!record.isUsable(now)) {
                failures.add(fail(i, uploadId, "이미 사용했거나 만료된 업로드 주소입니다."));
                continue;
            }
            usable[i] = record;
        }

        for (int i = 0; i < usable.length; i++) {
            UploadRecord record = usable[i];
            if (record == null) {
                continue;
            }
            if (!imageFormatValidator.isSupportedImage(record.getObjectKey())) {
                failures.add(fail(i, uploadIds.get(i), "실제 파일이 jpg, png, webp 형식이 아닙니다."));
            }
        }

        if (!failures.isEmpty()) {
            objectStore.deleteAll(cleanupTargets.stream().map(UploadRecord::getObjectKey).toList());
            consume(cleanupTargets);
            failures.sort(Comparator.comparingInt(UploadConfirmException.Failure::index));
            throw new UploadConfirmException(failures);
        }

        List<UploadRecord> confirmed = Arrays.asList(usable);
        consume(confirmed);
        return confirmed.stream().map(UploadRecord::getObjectKey).toList();
    }

    private void consume(List<UploadRecord> records) {
        records.forEach(UploadRecord::markUsed);
        uploadRecordRepository.saveAll(records);
    }

    private static UploadConfirmException.Failure fail(int index, UUID uploadId, String reason) {
        return new UploadConfirmException.Failure(index, uploadId, reason);
    }
}
