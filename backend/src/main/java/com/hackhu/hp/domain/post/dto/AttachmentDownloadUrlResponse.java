package com.hackhu.hp.domain.post.dto;

import java.net.URL;
import java.time.Instant;

/** 첨부 다운로드 주소 발급 응답 (4-2-11). 주소는 10분 뒤 만료된다. */
public record AttachmentDownloadUrlResponse(URL downloadUrl, Instant expiresAt) {}
