package com.hackhu.hp.domain.gallery.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 앨범 상세의 사진 한 장. S3 객체 키는 내려주지 않는다 (5-1 4절). */
@Schema(description = "사진")
public record PhotoResponse(
        @Schema(example = "101") Long id,
        @Schema(description = "CloudFront 서명 URL. 1시간 뒤 만료된다") String url,
        @Schema(description = "0부터 시작. 0번이 대표 사진", example = "0") int order) {}
