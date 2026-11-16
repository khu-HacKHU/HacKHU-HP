package com.hackhu.hp.domain.gallery.repository;

import com.hackhu.hp.domain.gallery.entity.Photo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    /** 목록 한 페이지의 대표 사진을 쿼리 한 번으로 가져온다 (앨범마다 조회하는 N+1 방지). */
    List<Photo> findByAlbumIdInAndDisplayOrder(Collection<Long> albumIds, short displayOrder);

    List<Photo> findByAlbumIdOrderByDisplayOrderAsc(Long albumId);
}
