package com.hackhu.hp.domain.user.dto;

import com.hackhu.hp.global.auth.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 역할 변경 요청 (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-13).
 *
 * <p>{@code role}을 받는 유일한 요청 DTO이고 관리자 검사를 통과한 뒤에만 쓴다 (5-1-8, CWE-915). 문자열로 받아 허용 목록으로 검사한다.
 * {@link Role}로 바로 받으면 {@code PENDING}이 통과하고, 없는 값은 칸별 사유 없는 400이 되기 때문이다.
 */
public record RoleChangeRequest(
        @Schema(allowableValues = {"MEMBER", "STAFF", "ADMIN"})
                @NotNull(message = "역할을 선택해주세요.")
                @Pattern(
                        regexp = "MEMBER|STAFF|ADMIN",
                        message = "역할은 MEMBER, STAFF, ADMIN 중 하나여야 합니다.")
                String role) {

    /** 검증을 통과한 뒤에만 부른다. */
    public Role toRole() {
        return Role.valueOf(role);
    }
}
