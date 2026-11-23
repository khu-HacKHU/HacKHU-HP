package com.hackhu.hp.domain.post.service;

import com.hackhu.hp.domain.post.dto.PostDetailResponse;
import com.hackhu.hp.domain.post.dto.PostPageResponse;
import com.hackhu.hp.domain.post.dto.PostWriteRequest;
import com.hackhu.hp.domain.post.entity.BoardType;
import com.hackhu.hp.domain.post.entity.Post;
import com.hackhu.hp.domain.post.repository.PostRepository;
import com.hackhu.hp.global.auth.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자유게시판 (#53, docs/spec/board/backend/4-2-INTERACTION-CONTRACT.md 4-2-8).
 *
 * <p>모든 메서드가 동아리원 이상이다. 클래스에 {@code @PreAuthorize}를 걸어 새 메서드를 더해도 빠지지 않는다. 글을 찾기 전에 역할로 막으므로 방문자와
 * 승인 대기 회원은 글 ID가 있든 없든 같은 응답을 받는다 (5-1-3, DECISIONS.md B7). Security 설정의 기본값(동아리원 이상)이 경로에서 한 번 더
 * 막는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
@PreAuthorize("hasRole('MEMBER')")
public class FreePostService {

    private static final BoardType BOARD = BoardType.FREE;

    private final PostRepository postRepository;
    private final BoardPostSupport support;

    @Transactional(readOnly = true)
    public PostPageResponse getPosts(int page, int size) {
        return support.page(BOARD, page, size);
    }

    @Transactional(readOnly = true)
    public PostDetailResponse getPost(Long postId) {
        return support.detail(support.find(BOARD, postId));
    }

    public PostDetailResponse create(LoginUser user, PostWriteRequest request) {
        // 작성자는 요청이 아니라 로그인한 사람이다 (5-1-3, CWE-915).
        Post post =
                postRepository.save(
                        Post.create(BOARD, request.title(), request.content(), user.id()));
        return support.detail(post);
    }

    public PostDetailResponse update(LoginUser user, Long postId, PostWriteRequest request) {
        Post post = support.findModifiable(BOARD, user, postId);
        post.edit(request.title(), request.content());
        postRepository.flush();
        support.logAdminAction(user, post, "수정");
        return support.detail(post);
    }

    public void delete(LoginUser user, Long postId) {
        Post post = support.findModifiable(BOARD, user, postId);
        postRepository.delete(post);
        postRepository.flush();
        support.logAdminAction(user, post, "삭제");
    }
}
