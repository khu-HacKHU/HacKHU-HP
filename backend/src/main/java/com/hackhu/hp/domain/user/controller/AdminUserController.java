package com.hackhu.hp.domain.user.controller;

import com.hackhu.hp.domain.user.dto.AdminUserPageResponse;
import com.hackhu.hp.domain.user.dto.AdminUserResponse;
import com.hackhu.hp.domain.user.dto.RoleChangeRequest;
import com.hackhu.hp.domain.user.service.AdminUserService;
import com.hackhu.hp.domain.user.service.UserRoleService;
import com.hackhu.hp.global.auth.CurrentUser;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.auth.Role;
import com.hackhu.hp.global.error.BusinessException;
import com.hackhu.hp.global.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 운영진의 회원 관리 API (docs/spec/auth/backend/4-2-INTERACTION-CONTRACT.md 4-2-10~14).
 *
 * <p>{@code /api/v1/admin/**}는 SecurityConfig가 운영진 이상으로 막고, 역할 변경만 관리자로 막는다.
 */
@Tag(name = "회원 관리", description = "운영진 이상. 역할 변경은 관리자")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    /** 허용하는 정렬은 가입 시각 하나다 (4-2-10). */
    private static final Set<Sort> ALLOWED_SORTS =
            Set.of(
                    Sort.by(Sort.Direction.DESC, "createdAt"),
                    Sort.by(Sort.Direction.ASC, "createdAt"));

    private final AdminUserService adminUserService;
    private final UserRoleService userRoleService;

    @Operation(
            summary = "회원 목록",
            description =
                    "운영진 이상. 탈퇴한 계정은 나오지 않는다. role이 없으면 모든 역할."
                            + " sort는 createdAt만 받고 기본은 createdAt,desc. size는 최대 100이고 넘으면 100으로 맞춘다.")
    @GetMapping
    public AdminUserPageResponse getUsers(
            @RequestParam(required = false) Role role,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return adminUserService.getUsers(
                role,
                pageable.getPageNumber(),
                pageable.getPageSize(),
                sortDirection(pageable.getSort()));
    }

    @Operation(
            summary = "가입 승인",
            description = "운영진 이상. 승인 대기 회원을 동아리원으로 바꾼다. 없거나 탈퇴 404, 승인 대기 아님 409")
    @PostMapping("/{userId}/approve")
    public AdminUserResponse approve(@PathVariable long userId, @CurrentUser LoginUser user) {
        return adminUserService.approve(user.id(), userId);
    }

    @Operation(
            summary = "가입 거절",
            description = "운영진 이상. 승인 대기 회원의 계정을 지운다. 없거나 탈퇴 404, 승인 대기 아님 409")
    @PostMapping("/{userId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable long userId, @CurrentUser LoginUser user) {
        adminUserService.reject(user.id(), userId);
    }

    // 다른 정렬 값을 쿼리에 넘기면 없는 필드에서 500이 난다. 허용 목록으로 먼저 막는다 (secure-coding 3절, CWE-89).
    private static Sort.Direction sortDirection(Sort sort) {
        if (sort.isUnsorted()) {
            return Sort.Direction.DESC;
        }
        if (!ALLOWED_SORTS.contains(sort)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return sort.iterator().next().getDirection();
    }

    @Operation(
            summary = "역할 변경",
            description =
                    "관리자. MEMBER, STAFF, ADMIN 중 하나로 바꾼다. 같은 역할이면 바꾸지 않고 200."
                            + " 없거나 탈퇴한 회원은 404, 승인 대기 회원, 마지막 활성 관리자, 처리 중 경합은 409")
    @PutMapping(path = "/{userId}/role", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminUserResponse changeRole(
            @CurrentUser LoginUser user,
            @PathVariable long userId,
            @Valid @RequestBody RoleChangeRequest request) {
        return userRoleService.changeRole(user.id(), userId, request.toRole());
    }
}
