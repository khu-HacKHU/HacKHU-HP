package com.hackhu.hp.domain.post.repository;

import com.hackhu.hp.domain.post.entity.BoardType;
import com.hackhu.hp.domain.post.entity.Post;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * 게시판 글 저장소.
 *
 * <p>세 게시판이 테이블 하나를 같이 쓰므로 조회는 모두 게시판 종류를 조건에 넣는다. {@code id}만으로 읽는 {@code findById}를 쓰지 못하게 {@code
 * JpaRepository}가 아니라 {@link Repository}를 상속하고 필요한 메서드만 연다. 조건을 빠뜨리면 공개 API인 공지로 동아리원 전용 글이 열리기
 * 때문이다 (5-1-4).
 */
public interface PostRepository extends Repository<Post, Long> {

    Post save(Post post);

    Optional<Post> findByIdAndBoardType(Long id, BoardType boardType);

    /**
     * 글 행을 {@code FOR UPDATE}로 잠그고 읽는다. 자료게시판 수정이 첨부 수를 세기 전에 잡아, 두 수정 요청이 동시에 와도 첨부가 5개를 넘지 않게 한다
     * (5-1-8).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :id and p.boardType = :boardType")
    Optional<Post> findForUpdate(Long id, BoardType boardType);

    Page<Post> findByBoardType(BoardType boardType, Pageable pageable);

    void delete(Post post);

    /** 수정 뒤 트리거가 바꾼 {@code updated_at}을 응답에 담으려면 먼저 반영해야 한다. */
    void flush();
}
