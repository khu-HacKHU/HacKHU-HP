package com.hackhu.hp.global.storage;

import java.util.List;
import java.util.UUID;

/**
 * 업로드 확정이 실패했을 때 던진다. 첫 실패에서 끊지 않고 전부 모아서 담는다. 사용자가 하나 고치고 다시 보냈다가 또 걸리는 일을 줄이려는 것이다
 * (docs/spec/gallery/backend/4-2 4절).
 */
public class UploadConfirmException extends RuntimeException {

    /**
     * @param index 요청 배열에서 몇 번째인지
     */
    public record Failure(int index, UUID uploadId, String reason) {}

    private final List<Failure> failures;

    public UploadConfirmException(List<Failure> failures) {
        super("업로드 확정 실패 " + failures.size() + "건");
        this.failures = List.copyOf(failures);
    }

    public List<Failure> getFailures() {
        return failures;
    }
}
