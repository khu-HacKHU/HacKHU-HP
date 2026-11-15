package com.hackhu.hp.domain.gallery.dto;

import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 업로드 주소 발급 응답 (docs/spec/gallery/backend/4-2 3절). S3 객체 키는 내려주지 않는다. 클라이언트는 uploadId로만 확정 요청을 보낸다.
 */
public record UploadUrlResponse(List<Upload> uploads) {

    public record Upload(UUID uploadId, URL uploadUrl, Instant expiresAt) {}
}
