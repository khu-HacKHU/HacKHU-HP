package com.hackhu.hp.domain.post.dto;

import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 첨부 업로드 주소 발급 응답 (4-2-10). 객체 키는 내려주지 않는다. 클라이언트는 {@code uploadId}만 다룬다. */
public record AttachmentUploadUrlResponse(List<Upload> uploads) {

    public record Upload(UUID uploadId, URL uploadUrl, Instant expiresAt) {}
}
