package com.hackhu.hp.domain.post.service;

import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 임시 구현. 인증권한팀에 사용자 ID 목록으로 이름을 한 번에 읽는 공개 메서드가 아직 없어서 이름을 하나도 돌려주지 않는다. 게시판이 {@code users} 테이블이나
 * {@code UserRepository}를 직접 읽지 않는다 (backend/AGENTS.md).
 *
 * <p>이 상태로는 모든 작성자가 "탈퇴한 회원"으로 보이므로 #29를 머지하기 전에 반드시 바꾼다.
 *
 * <p>TODO(personaoscura): 인증권한팀이 제공하는 이름 조회 메서드로 바꾸고 이 클래스를 지운다 #29
 */
@Component
class PendingAuthorNameReader implements AuthorNameReader {

    @Override
    public Map<Long, String> findNames(Collection<Long> authorIds) {
        return Map.of();
    }
}
