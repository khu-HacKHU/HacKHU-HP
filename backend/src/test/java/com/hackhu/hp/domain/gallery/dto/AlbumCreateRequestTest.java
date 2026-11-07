package com.hackhu.hp.domain.gallery.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AlbumCreateRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> violatedFields(AlbumCreateRequest request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static List<UUID> uploadIds(int count) {
        return java.util.stream.Stream.generate(UUID::randomUUID).limit(count).toList();
    }

    @Test
    void 정상_요청은_위반이_없다() {
        AlbumCreateRequest request =
                new AlbumCreateRequest("10월 정기 세미나", LocalDate.of(2026, 10, 18), uploadIds(3));

        assertTrue(violatedFields(request).isEmpty());
    }

    @Test
    void 제목이_비면_거부한다() {
        AlbumCreateRequest request =
                new AlbumCreateRequest("  ", LocalDate.of(2026, 10, 18), uploadIds(1));

        assertEquals(Set.of("title"), violatedFields(request));
    }

    @Test
    void 제목이_백자를_넘으면_거부한다() {
        AlbumCreateRequest request =
                new AlbumCreateRequest("가".repeat(101), LocalDate.of(2026, 10, 18), uploadIds(1));

        assertEquals(Set.of("title"), violatedFields(request));
    }

    @Test
    void 활동_날짜가_없으면_거부한다() {
        AlbumCreateRequest request = new AlbumCreateRequest("제목", null, uploadIds(1));

        assertEquals(Set.of("eventDate"), violatedFields(request));
    }

    @Test
    void 사진이_없으면_거부한다() {
        AlbumCreateRequest request =
                new AlbumCreateRequest("제목", LocalDate.of(2026, 10, 18), Collections.emptyList());

        assertEquals(Set.of("uploadIds"), violatedFields(request));
    }

    @Test
    void 사진이_스물한_장이면_거부한다() {
        AlbumCreateRequest request =
                new AlbumCreateRequest("제목", LocalDate.of(2026, 10, 18), uploadIds(21));

        assertEquals(Set.of("uploadIds"), violatedFields(request));
    }

    @Test
    void 사진이_스무_장이면_통과한다() {
        AlbumCreateRequest request =
                new AlbumCreateRequest("제목", LocalDate.of(2026, 10, 18), uploadIds(20));

        assertTrue(violatedFields(request).isEmpty());
    }

    /**
     * 미래 날짜는 DTO 검증을 통과해야 한다. {@code @PastOrPresent}를 붙이면 JVM 기본 시간대로 "오늘"을 판단해서, 서버가 UTC일 때 한국 시간
     * 자정부터 오전 9시 사이에 올리는 사람이 자기 기준 오늘을 미래로 거부당한다. 그래서 이 검사는 한국 시간 기준으로 서비스에서 한다
     * (GALLERY_FUTURE_EVENT_DATE).
     */
    @Test
    void 미래_날짜는_DTO에서_걸러지지_않는다() {
        AlbumCreateRequest request =
                new AlbumCreateRequest("제목", LocalDate.now().plusYears(1), uploadIds(1));

        assertTrue(violatedFields(request).isEmpty());
    }
}
