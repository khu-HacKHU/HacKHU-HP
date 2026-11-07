package com.hackhu.hp.domain.post.service;

import com.hackhu.hp.domain.post.dto.NoticeDetailResponse;
import com.hackhu.hp.domain.post.dto.NoticePageResponse;
import com.hackhu.hp.domain.post.dto.NoticeSummaryResponse;
import com.hackhu.hp.domain.post.entity.BoardType;
import com.hackhu.hp.domain.post.entity.Post;
import com.hackhu.hp.domain.post.exception.PostErrorCode;
import com.hackhu.hp.domain.post.repository.PostRepository;
import com.hackhu.hp.global.error.BusinessException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공지 목록과 상세 조회 (#29). 둘 다 공개 API라 권한 검사가 없다 (5-1-2). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeQueryService {

    /** 한 페이지 최대 글 수. 게시판 화면은 20개, 메인 화면은 5개를 쓴다 (2-REQUIREMENTS.md 2-2). */
    public static final int MAX_PAGE_SIZE = 20;

    /**
     * 최신순. 작성 시각이 같으면 id로 순서를 고정해 페이지를 넘길 때 글이 겹치거나 빠지지 않게 한다. 정렬 기준은 서버가 정하고 클라이언트 값은 쓰지 않는다
     * (5-1-5, CWE-89).
     */
    private static final Sort LIST_SORT =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PostRepository postRepository;
    private final AuthorNameReader authorNameReader;

    public NoticePageResponse getNotices(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), LIST_SORT);
        Page<Post> notices = postRepository.findByBoardType(BoardType.NOTICE, pageRequest);
        Map<Long, String> names = authorNames(notices.getContent());
        return NoticePageResponse.from(
                notices.map(post -> NoticeSummaryResponse.of(post, names.get(post.getAuthorId()))));
    }

    public NoticeDetailResponse getNotice(Long noticeId) {
        // 공지 조건으로만 찾는다. 다른 게시판 글 ID면 없는 글과 같게 404 (5-1-4).
        Post notice =
                postRepository
                        .findByIdAndBoardType(noticeId, BoardType.NOTICE)
                        .orElseThrow(() -> new BusinessException(PostErrorCode.POST_NOT_FOUND));
        Map<Long, String> names = authorNames(List.of(notice));
        return NoticeDetailResponse.of(notice, names.get(notice.getAuthorId()));
    }

    // 목록 한 페이지의 작성자 이름을 한 번에 읽는다. 글마다 읽으면 20개 페이지에 조회가 20번 생긴다.
    private Map<Long, String> authorNames(List<Post> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        List<Long> authorIds = posts.stream().map(Post::getAuthorId).distinct().toList();
        return authorNameReader.findNames(authorIds);
    }
}
