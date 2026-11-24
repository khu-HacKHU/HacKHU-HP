package com.hackhu.hp.domain.gallery.repository;

import com.hackhu.hp.domain.gallery.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    /**
     * 앨범 행을 지우고 지운 행 수를 준다. 사진 행은 DB의 ON DELETE CASCADE로 같이 지워진다 (3-2 3절).
     *
     * <p>{@code delete(entity)}를 쓰지 않는다. 같은 앨범을 동시에 두 번 지우면 늦은 쪽이 0행을 지우는데, 엔티티 삭제는 그때 낙관적 잠금 예외를
     * 던져 500이 된다. 행 수를 받아 0이면 404로 바꾼다 (5-2 5-2-2).
     */
    @Modifying
    @Query("delete from Album a where a.id = :albumId")
    int deleteAlbumById(@Param("albumId") long albumId);
}
