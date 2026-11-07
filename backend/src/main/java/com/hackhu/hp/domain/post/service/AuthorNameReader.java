package com.hackhu.hp.domain.post.service;

import java.util.Collection;
import java.util.Map;

/**
 * 작성자 ID로 화면에 보여 줄 이름을 읽는다 (4-2-INTERACTION-CONTRACT.md 4-2-1).
 *
 * <p>회원 정보는 인증권한팀 도메인이라 게시판이 {@code users} 테이블이나 {@code User} 엔티티를 직접 읽지 않는다 (backend/AGENTS.md "다른
 * 팀의 domain/* 내부 클래스를 직접 부르지 않는다"). 이 인터페이스 뒤에 두고, 구현은 인증권한팀이 제공하는 조회 방법이 생기면 바꾼다.
 */
public interface AuthorNameReader {

    /**
     * 작성자 이름을 한 번에 읽는다. 목록 한 페이지에 쿼리 한 번만 쓰도록 ID를 모아서 받는다.
     *
     * @return 작성자 ID와 이름. 탈퇴해서 이름이 지워진 작성자는 값이 {@code null}이거나 맵에 없다. 응답에서는 둘 다 {@code null}로 나가고
     *     화면은 "탈퇴한 회원"으로 보여 준다
     */
    Map<Long, String> findNames(Collection<Long> authorIds);
}
