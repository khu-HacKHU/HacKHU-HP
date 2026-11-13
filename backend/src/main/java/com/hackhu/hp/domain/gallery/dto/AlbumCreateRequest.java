package com.hackhu.hp.domain.gallery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 앨범 올리기 요청 (docs/spec/gallery/backend/4-2 4절).
 *
 * <p>`uploadIds` 배열 순서가 사진 순서이고 0번째가 대표 사진이다. 업로더 ID나 역할은 받지 않는다. 현재 사용자는 인증 정보에서만 꺼낸다 (Mass
 * Assignment 대비, 5-1 4절).
 *
 * <p>`eventDate`에 미래 날짜를 금지하는 검사는 여기 애너테이션으로 하지 않는다. {@code @PastOrPresent}는 JVM 기본 시간대로 "오늘"을
 * 판단하는데, 서버가 UTC로 돌면 한국 시간 자정부터 오전 9시 사이에 올리는 사람은 자기 기준 오늘 날짜를 미래로 거부당한다. 행사 끝나고 밤에 올리는 경우가 흔해서, 한국
 * 시간 기준으로 서비스에서 검사한다 (5-3 1절).
 */
public record AlbumCreateRequest(
        @NotBlank(message = "제목을 입력해주세요.") @Size(max = 100, message = "제목은 100자 이하로 입력해주세요.")
                String title,
        @NotNull(message = "활동 날짜를 입력해주세요.") LocalDate eventDate,
        @NotNull @Size(min = 1, max = 20, message = "사진은 1장 이상 20장 이하로 올릴 수 있습니다.")
                List<UUID> uploadIds) {}
