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
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 발급받은 업로드를 확정한다. 갤러리 앨범({@link #confirmImages})과 자료게시판 첨부({@link #confirmAttachments})가 같이 쓴다.
 *
 * <p>검사 순서는 싼 것부터다. 발급 기록 조회(소유자·만료·중복)로 먼저 걸러내고, 통과한 것만 S3에서 실제 바이트를 읽어 매직바이트를 본다
 * (docs/spec/gallery/backend/4-2 4절).
 *
 * <p>이 클래스에는 트랜잭션을 걸지 않는다. 사용 처리는 레포지토리의 조건부 갱신이 하고, 그 메서드가 {@code @Transactional}이라 호출하는 쪽이 트랜잭션을
 * 걸었는지와 무관하게 동작한다. 여기에 트랜잭션을 걸면 실패 경로에서 S3 객체를 지운 뒤 예외를 던질 때 기록 갱신만 롤백돼 S3와 DB가 어긋난다.
 */
@Service
public class UploadConfirmService {

    private static final Logger log = LoggerFactory.getLogger(UploadConfirmService.class);

    /** 업로드 종류마다 다른 것: 형식 확인 방법과 사용자에게 보일 사유 문구. */
    private record Rule(
            Predicate<UploadRecord> formatCheck, String duplicateReason, String formatReason) {}

    private final UploadRecordRepository uploadRecordRepository;
    private final ImageFormatValidator imageFormatValidator;
    private final AttachmentFormatValidator attachmentFormatValidator;
    private final S3ObjectStore objectStore;

    public UploadConfirmService(
            UploadRecordRepository uploadRecordRepository,
            ImageFormatValidator imageFormatValidator,
            AttachmentFormatValidator attachmentFormatValidator,
            S3ObjectStore objectStore) {
        this.uploadRecordRepository = uploadRecordRepository;
        this.imageFormatValidator = imageFormatValidator;
        this.attachmentFormatValidator = attachmentFormatValidator;
        this.objectStore = objectStore;
    }

    /**
     * 전부 통과하면 요청 순서대로 object key를 돌려주고, 기록을 USED로 바꾼다. 하나라도 실패하면 이번 요청에서 아직 안 쓰인 객체를 모두 지우고 그 기록들을
     * 소비 처리한 뒤 {@link UploadConfirmException}을 던진다.
     *
     * <p>소유자·만료·중복·매직바이트 검사를 전부 통과해도, 사용 처리는 {@link UploadRecordRepository#claim}의 조건부 갱신으로 한다. 같은
     * uploadId를 두 요청이 동시에 확정해도 갱신은 하나만 성공하므로, 다른 하나는 갱신된 행 수가 모자라 거부된다 (board 5-1-8, CWE-362).
     */
    public List<String> confirmImages(Long userId, UploadPurpose purpose, List<UUID> uploadIds) {
        Rule rule =
                new Rule(
                        record -> imageFormatValidator.isSupportedImage(record.getObjectKey()),
                        "같은 사진을 두 번 넣을 수 없습니다.",
                        "실제 파일이 jpg, png, webp 형식이 아닙니다.");
        return confirm(userId, purpose, uploadIds, rule).stream()
                .map(UploadRecord::getObjectKey)
                .toList();
    }

    /**
     * 자료게시판 첨부를 확정한다 (board 5-1-8). 검사 순서, 실패 시 정리, 조건부 갱신은 {@link #confirmImages}와 같고 형식 확인만 다르다.
     * 첨부 행을 만들 때 형식, 크기, 원래 파일 이름을 발급 기록에서 옮겨 적어야 해서 키가 아니라 기록을 요청 순서대로 돌려준다 (board 5-1-7).
     */
    public List<UploadRecord> confirmAttachments(Long userId, List<UUID> uploadIds) {
        Rule rule =
                new Rule(
                        attachmentFormatValidator::matchesDeclaredType,
                        "같은 파일을 두 번 넣을 수 없습니다.",
                        "실제 파일이 신고한 형식이 아닙니다.");
        return confirm(userId, UploadPurpose.ATTACHMENT, uploadIds, rule);
    }

    private List<UploadRecord> confirm(
            Long userId, UploadPurpose purpose, List<UUID> uploadIds, Rule rule) {
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
                failures.add(fail(i, uploadId, rule.duplicateReason()));
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
            if (!rule.formatCheck().test(record)) {
                // 형식 확인 실패는 경고로 남긴다 (board 5-1-12). 객체 키와 파일 이름은 남기지 않는다.
                log.warn(
                        "업로드 형식 확인 실패 userId={} uploadId={} purpose={} declaredType={}",
                        userId,
                        record.getId(),
                        purpose,
                        record.getContentType());
                failures.add(fail(i, uploadIds.get(i), rule.formatReason()));
            }
        }

        if (!failures.isEmpty()) {
            discard(cleanupTargets, userId, now);
            failures.sort(Comparator.comparingInt(UploadConfirmException.Failure::index));
            throw new UploadConfirmException(failures);
        }

        // 조건부 갱신으로 사용 처리한다. 여기까지의 검사는 읽기라서, 같은 uploadId를 두 요청이 동시에
        // 확정하면 둘 다 통과한다. 갱신된 행 수가 요청 수와 다르면 경합에서 밀린 것이므로 전체를 거부한다
        // (board 5-1-8, CWE-362).
        int claimed = uploadRecordRepository.claim(uploadIds, userId, now);
        if (claimed != uploadIds.size()) {
            throw new UploadConfirmException(lostRaceFailures(uploadIds, userId, now));
        }

        return Arrays.asList(usable);
    }

    /** 실패한 요청의 객체를 지우고, 아직 안 쓰인 기록을 사용 처리해 재시도로 S3 읽기를 반복하지 못하게 한다. */
    private void discard(List<UploadRecord> cleanupTargets, Long userId, Instant now) {
        objectStore.deleteAll(cleanupTargets.stream().map(UploadRecord::getObjectKey).toList());
        uploadRecordRepository.claim(
                cleanupTargets.stream().map(UploadRecord::getId).toList(), userId, now);
    }

    /**
     * 경합에서 밀렸을 때 어느 항목이 문제인지 다시 읽어 알려준다. 드문 경로라 조회를 한 번 더 해도 된다. 사유는 이미 쓰인 업로드와 같은 문구를 쓴다. 사용자 입장에서
     * 같은 상황이기 때문이다.
     */
    private List<UploadConfirmException.Failure> lostRaceFailures(
            List<UUID> uploadIds, Long userId, Instant now) {
        Map<UUID, UploadRecord> current =
                uploadRecordRepository.findAllById(uploadIds).stream()
                        .collect(Collectors.toMap(UploadRecord::getId, Function.identity()));
        List<UploadConfirmException.Failure> failures = new ArrayList<>();
        for (int i = 0; i < uploadIds.size(); i++) {
            UUID uploadId = uploadIds.get(i);
            UploadRecord record = current.get(uploadId);
            if (record == null || !record.isOwnedBy(userId) || !record.isUsable(now)) {
                failures.add(fail(i, uploadId, "이미 사용했거나 만료된 업로드 주소입니다."));
            }
        }
        if (failures.isEmpty()) {
            // 갱신 수가 안 맞았는데 다시 읽으니 전부 멀쩡하면 어느 것이 밀렸는지 가릴 수 없다.
            // 사유 없는 응답을 주지 않도록 전체를 실패로 알린다.
            for (int i = 0; i < uploadIds.size(); i++) {
                failures.add(fail(i, uploadIds.get(i), "이미 사용했거나 만료된 업로드 주소입니다."));
            }
        }
        return failures;
    }

    private static UploadConfirmException.Failure fail(int index, UUID uploadId, String reason) {
        return new UploadConfirmException.Failure(index, uploadId, reason);
    }
}
