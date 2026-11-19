package com.hackhu.hp.domain.user.service;

import com.hackhu.hp.domain.user.repository.UserDisplayName;
import com.hackhu.hp.domain.user.repository.UserRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 기능이 회원 정보를 읽는 입구 (backend/AGENTS.md "다른 팀의 {@code domain/*} 내부 클래스를 직접 부르지 않는다").
 *
 * <p>게시판과 갤러리는 {@code users} 테이블이나 {@link UserRepository}를 직접 읽지 않고 이 서비스를 부른다. 그래야 어떤 회원 정보가 다른
 * 기능으로 나가는지가 이 클래스 한 곳에 모인다.
 *
 * <p>여기서 돌려주는 것은 화면에 보여 줄 이름뿐이다. 이메일, 역할, 비밀번호 해시, {@code User} 엔티티는 돌려주지 않는다 (auth
 * 5-1-QUALITY-SECURITY.md 5-1-9).
 */
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;

    /**
     * 화면에 보여 줄 회원 이름을 한 번에 읽는다. 목록 한 페이지에 쿼리 한 번이다.
     *
     * <p>탈퇴한 회원은 맵에 없다. 부르는 쪽이 {@link Map#get}으로 꺼내 없으면 "탈퇴한 회원"처럼 표시한다. 값이 {@code null}인 항목은 없다.
     *
     * <p>없는 ID도 맵에 없다. 두 경우를 구분하지 않는다. 구분해서 주면 ID로 계정이 있었는지를 떠볼 수 있다.
     *
     * @param userIds 읽을 회원 ID. 중복은 합쳐서 한 번만 조회한다. 비어 있으면 DB를 부르지 않는다
     * @return 회원 ID에서 이름으로. 바꿀 수 없는 맵이다
     */
    @Transactional(readOnly = true)
    public Map<Long, String> findDisplayNames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Collection<Long> distinctIds = new HashSet<>(userIds);
        distinctIds.remove(null);
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findDisplayNames(distinctIds).stream()
                .collect(Collectors.toUnmodifiableMap(UserDisplayName::id, UserDisplayName::name));
    }
}
