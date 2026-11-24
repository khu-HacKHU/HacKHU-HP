package com.hackhu.hp.domain.post.service;

import com.hackhu.hp.domain.post.dto.PostDetailResponse;
import com.hackhu.hp.domain.post.dto.PostPageResponse;
import com.hackhu.hp.domain.post.dto.PostSummaryResponse;
import com.hackhu.hp.domain.post.entity.BoardType;
import com.hackhu.hp.domain.post.entity.Post;
import com.hackhu.hp.domain.post.exception.PostErrorCode;
import com.hackhu.hp.domain.post.repository.PostRepository;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * 동아리원 전용 게시판(자유게시판, 자료게시판)이 함께 쓰는 글 읽기, 권한 확인, 응답 만들기.
 *
 * <p>인가는 하지 않는다. 역할 확인은 게시판마다 서비스의 {@code @PreAuthorize}가 먼저 한다 (5-1-3 1단계). 여기는 2단계(게시판 종류 조건으로 글
 * 읽기)와 3단계(작성자 확인)를 맡는다. 모든 조회가 게시판 종류를 받으므로 다른 게시판 글이 섞이지 않는다 (5-1-4).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class BoardPostSupport {

    /** 한 페이지 최대 글 수 (2-REQUIREMENTS.md 2-2). 전역 설정(100)으로는 걸리지 않아 직접 맞춘다. */
    static final int MAX_PAGE_SIZE = 20;

    /** 최신순. 작성 시각이 같으면 id로 순서를 고정한다 (4-2-3). 클라이언트 값은 쓰지 않는다 (CWE-89). */
    private static final Sort LIST_SORT =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PostRepository postRepository;
    private final AuthorNameReader authorNameReader;

    PostPageResponse page(BoardType boardType, int page, int size) {
        Page<Post> posts = findPage(boardType, page, size);
        Map<Long, String> names = authorNames(posts.getContent());
        return PostPageResponse.from(
                posts.map(post -> PostSummaryResponse.of(post, names.get(post.getAuthorId()))));
    }

    /** 최신순 한 페이지. 페이지 크기는 20을 넘지 않는다. */
    Page<Post> findPage(BoardType boardType, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), LIST_SORT);
        return postRepository.findByBoardType(boardType, pageRequest);
    }

    /** 게시판 종류 조건으로만 찾는다. 다른 게시판 글 ID면 없는 글과 같게 404 (5-1-4). */
    Post find(BoardType boardType, Long postId) {
        return postRepository
                .findByIdAndBoardType(postId, boardType)
                .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
    }

    /** 작성자이거나 관리자만 통과한다. 작성자는 DB의 author_id와 로그인한 사람을 비교한다 (5-1-3, CWE-639). */
    Post findModifiable(BoardType boardType, LoginUser user, Long postId) {
        Post post = find(boardType, postId);
        checkModifiable(user, post);
        return post;
    }

    /** 이미 읽은 글(예: 잠그고 읽은 글)에 작성자 규칙을 확인한다. */
    void checkModifiable(LoginUser user, Post post) {
        if (!isAuthor(user, post) && !user.isAdmin()) {
            throw new BusinessException(PostErrorCode.POST_FORBIDDEN);
        }
    }

    /**
     * 관리자가 남의 글을 고치거나 지운 기록 (5-1-12, secure-coding.md 1절). DB에 반영(flush)한 뒤에 부른다. 제목과 본문은 남기지 않는다.
     */
    void logAdminAction(LoginUser user, Post post, String action) {
        if (isAuthor(user, post)) {
            return;
        }
        log.info(
                "관리자가 남의 글을 {}함 adminId={} postId={} boardType={} authorId={}",
                action,
                user.id(),
                post.getId(),
                post.getBoardType(),
                post.getAuthorId());
    }

    PostDetailResponse detail(Post post) {
        return PostDetailResponse.of(post, authorNames(List.of(post)).get(post.getAuthorId()));
    }

    Map<Long, String> authorNames(List<Post> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        List<Long> authorIds = posts.stream().map(Post::getAuthorId).distinct().toList();
        return authorNameReader.findNames(authorIds);
    }

    private static boolean isAuthor(LoginUser user, Post post) {
        return Objects.equals(post.getAuthorId(), user.id());
    }
}
