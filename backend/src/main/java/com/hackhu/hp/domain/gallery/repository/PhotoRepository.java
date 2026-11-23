package com.hackhu.hp.domain.gallery.repository;

import com.hackhu.hp.domain.gallery.entity.Photo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    /** 목록 한 페이지의 대표 사진을 쿼리 한 번으로 가져온다 (앨범마다 조회하는 N+1 방지). */
    List<Photo> findByAlbumIdInAndDisplayOrder(Collection<Long> albumIds, short displayOrder);

    List<Photo> findByAlbumIdOrderByDisplayOrderAsc(Long albumId);

    /** 목록 한 페이지의 앨범별 사진 수를 쿼리 한 번으로 센다 (N+1 방지). */
    @Query(
            "select p.albumId as albumId, count(p) as photoCount from Photo p"
                    + " where p.albumId in :albumIds group by p.albumId")
    List<AlbumPhotoCount> countByAlbumIds(@Param("albumIds") Collection<Long> albumIds);

    /** {@link #countByAlbumIds}의 결과 한 줄. */
    interface AlbumPhotoCount {
        Long getAlbumId();

        Long getPhotoCount();
    }
}
