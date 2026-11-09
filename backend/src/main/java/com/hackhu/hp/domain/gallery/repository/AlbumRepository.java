package com.hackhu.hp.domain.gallery.repository;

import com.hackhu.hp.domain.gallery.entity.Album;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlbumRepository extends JpaRepository<Album, Long> {}
