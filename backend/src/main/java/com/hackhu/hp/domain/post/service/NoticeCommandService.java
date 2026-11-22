package com.hackhu.hp.domain.post.service;

import com.hackhu.hp.domain.post.dto.NoticeDetailResponse;
import com.hackhu.hp.domain.post.dto.NoticeWriteRequest;
import com.hackhu.hp.domain.post.entity.BoardType;
import com.hackhu.hp.domain.post.entity.Post;
import com.hackhu.hp.domain.post.exception.PostErrorCode;
import com.hackhu.hp.domain.post.repository.PostRepository;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.error.BusinessException;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 작성, 수정, 삭제 (#32).
 *
 * <p>검사 순서는 5-1-3을 따른다. 1) 운영진 이상인지 ({@code @PreAuthorize}, 아니면 403 {@code FORBIDDEN}) 2) {@code
 * NOTICE} 조건으로 글을 읽는다 (없으면 404) 3) 작성자이거나 관리자인지 (아니면 403 {@code POST_FORBIDDEN}).
 *
 * <p>운영진에서 동아리원으로 내려간 사람은 1단계에서 막혀 자기 공지도 고칠 수 없다 (DECISIONS.md B4). 역할은 요청마다 DB에서 읽은 값이다 (auth
 * A2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
@PreAuthorize("hasRole('STAFF')")
public class NoticeCommandService {

    private final PostRepository postRepository;
    private final AuthorNameReader authorNameReader;

    public NoticeDetailResponse create(LoginUser user, NoticeWriteRequest request) {
        // 작성자는 요청이 아니라 로그인한 사람이다 (5-1-3, CWE-915).
        Post notice =
                postRepository.save(
                        Post.create(
                                BoardType.NOTICE, request.title(), request.content(), user.id()));
        return toResponse(notice);
    }

    public NoticeDetailResponse update(LoginUser user, Long noticeId, NoticeWriteRequest request) {
        Post notice = findModifiable(user, noticeId);
        notice.edit(request.title(), request.content());
        // DB 제약 위반은 여기서 드러난다. 관리자 기록은 반영이 끝난 뒤에 남긴다 (PR #114 리뷰).
        postRepository.flush();
        logAdminAction(user, notice, "수정");
        return toResponse(notice);
    }

    public void delete(LoginUser user, Long noticeId) {
        Post notice = findModifiable(user, noticeId);
        postRepository.delete(notice);
        postRepository.flush();
        logAdminAction(user, notice, "삭제");
    }

    private Post findModifiable(LoginUser user, Long noticeId) {
        // 공지 조건으로만 찾는다. 다른 게시판 글 ID면 없는 글과 같게 404 (5-1-4).
        Post notice =
                postRepository
                        .findByIdAndBoardType(noticeId, BoardType.NOTICE)
                        .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
        // 작성자 확인은 DB의 author_id와 로그인한 사람을 비교한다 (CWE-639).
        if (!isAuthor(user, notice) && !user.isAdmin()) {
            throw new BusinessException(PostErrorCode.POST_FORBIDDEN);
        }
        return notice;
    }

    /**
     * 관리자가 남의 공지를 고치거나 지운 기록 (5-1-8, secure-coding.md 1절). 제목과 본문은 남기지 않는다.
     *
     * <p>DB에 반영(flush)한 뒤에 부른다. 거부되거나 반영에 실패한 요청은 기록하지 않는다. 커밋은 메서드가 끝날 때 일어나므로 커밋 실패까지 가려내지는 못한다.
     * 그것까지 하려면 커밋 뒤 콜백이 필요한데 지금 규모에는 과하다 (PR #114 리뷰).
     */
    private void logAdminAction(LoginUser user, Post notice, String action) {
        if (isAuthor(user, notice)) {
            return;
        }
        log.info(
                "관리자가 남의 공지를 {}함 adminId={} postId={} authorId={}",
                action,
                user.id(),
                notice.getId(),
                notice.getAuthorId());
    }

    private static boolean isAuthor(LoginUser user, Post notice) {
        return Objects.equals(notice.getAuthorId(), user.id());
    }

    private NoticeDetailResponse toResponse(Post notice) {
        String name =
                authorNameReader.findNames(List.of(notice.getAuthorId())).get(notice.getAuthorId());
        return NoticeDetailResponse.of(notice, name);
    }
}
