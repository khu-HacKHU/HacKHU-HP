package com.hackhu.hp.domain.user.repository;

/**
 * 화면에 보여 줄 회원 이름만 담은 조회 결과 ({@link UserRepository#findDisplayNames}).
 *
 * <p>쿼리가 {@code id}와 {@code name}만 선택하므로 이메일, 역할, 비밀번호 해시는 DB에서 나오지 않는다 (auth
 * 5-1-QUALITY-SECURITY.md 5-1-9).
 *
 * <p>JPQL 생성자 표현식이 엔티티 필드 타입과 같은 래퍼 타입을 받는다 ({@code SessionPrincipal}과 같은 이유).
 */
public record UserDisplayName(Long id, String name) {}
