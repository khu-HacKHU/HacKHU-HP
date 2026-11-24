package com.hackhu.hp.domain.post.service;

import com.hackhu.hp.domain.user.service.UserQueryService;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 작성자 이름 조회를 인증권한팀의 {@link UserQueryService}에 위임한다 (#115).
 *
 * <p>게시판은 {@code users} 테이블이나 {@code UserRepository}를 직접 읽지 않고 이 어댑터를 거친다 (backend/AGENTS.md "다른 팀의
 * {@code domain/*} 내부 클래스를 직접 부르지 않는다"). 그래서 게시판이 회원 정보에 닿는 지점이 이 클래스 하나로 남는다.
 *
 * <p>여기서 더하거나 거르는 것은 없다. 탈퇴한 회원과 없는 ID는 {@link UserQueryService#findDisplayNames}에서 이미 빠져 나오고,
 * {@link AuthorNameReader} 계약이 그 두 경우를 모두 "맵에 없음"으로 받게 되어 있다 (4-2-INTERACTION-CONTRACT.md 4-2-1).
 */
@Component
@RequiredArgsConstructor
class UserAuthorNameReader implements AuthorNameReader {

    private final UserQueryService userQueryService;

    @Override
    public Map<Long, String> findNames(Collection<Long> authorIds) {
        return userQueryService.findDisplayNames(authorIds);
    }
}
