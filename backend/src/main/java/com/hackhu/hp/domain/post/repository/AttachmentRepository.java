package com.hackhu.hp.domain.post.repository;

import com.hackhu.hp.domain.post.entity.Attachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 자료게시판 첨부 저장소.
 *
 * <p>첨부는 항상 글을 거쳐 찾는다. {@code id}만으로 읽는 메서드를 열지 않아 다른 글의 첨부 ID로 접근하지 못하게 한다 (5-1-4, DECISIONS.md
 * B5).
 */
public interface AttachmentRepository extends Repository<Attachment, Long> {

    Attachment save(Attachment attachment);

    List<Attachment> findByPostIdOrderByDisplayOrderAsc(Long postId);

    Optional<Attachment> findByIdAndPostId(Long id, Long postId);

    void deleteAll(Iterable<Attachment> attachments);

    void flush();

    /** 목록의 {@code attachmentCount}. 첨부가 없는 글은 결과에 없다. */
    @Query(
            """
            select a.postId as postId, count(a) as attachmentCount
              from Attachment a
             where a.postId in :postIds
             group by a.postId
            """)
    List<PostAttachmentCount> countByPostIds(Collection<Long> postIds);

    interface PostAttachmentCount {
        Long getPostId();

        long getAttachmentCount();
    }
}
