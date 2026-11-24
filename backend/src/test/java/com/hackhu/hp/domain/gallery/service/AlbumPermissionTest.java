package com.hackhu.hp.domain.gallery.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hackhu.hp.domain.gallery.entity.Album;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.auth.Role;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 삭제 API와 상세 응답의 canDelete가 함께 쓰는 규칙 (5-1 1절). */
class AlbumPermissionTest {

    private static final long UPLOADER = 7L;
    private final Album album = Album.create("MT", LocalDate.of(2026, 9, 1), UPLOADER);

    @Test
    void 비로그인은_지울_수_없다() {
        assertFalse(AlbumPermission.canDelete(album, null));
    }

    @Test
    void 작성자는_지울_수_있다() {
        assertTrue(AlbumPermission.canDelete(album, new LoginUser(UPLOADER, Role.MEMBER)));
    }

    @Test
    void 다른_동아리원은_지울_수_없다() {
        assertFalse(AlbumPermission.canDelete(album, new LoginUser(8L, Role.MEMBER)));
    }

    @Test
    void 운영진은_관리자가_아니라_남의_앨범을_지울_수_없다() {
        assertFalse(AlbumPermission.canDelete(album, new LoginUser(8L, Role.STAFF)));
    }

    @Test
    void 관리자는_남의_앨범도_지울_수_있다() {
        assertTrue(AlbumPermission.canDelete(album, new LoginUser(1L, Role.ADMIN)));
    }

    @Test
    void 승인_대기로_내려간_작성자는_지울_수_없다() {
        // 삭제 API는 SecurityConfig 기본값(동아리원 이상)이 먼저 막는다. 버튼만 보이는 일이 없게 같은 조건을 건다.
        assertFalse(AlbumPermission.canDelete(album, new LoginUser(UPLOADER, Role.PENDING)));
    }
}
