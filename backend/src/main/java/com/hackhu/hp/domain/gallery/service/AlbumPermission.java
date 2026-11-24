package com.hackhu.hp.domain.gallery.service;

import com.hackhu.hp.domain.gallery.entity.Album;
import com.hackhu.hp.global.auth.LoginUser;
import com.hackhu.hp.global.auth.Role;

/**
 * 앨범 삭제 권한 규칙 (5-1 1절). 삭제 API와 상세 응답의 {@code canDelete}가 같은 규칙을 쓰도록 한 곳에 둔다. 둘이 따로 놀면 버튼은 보이는데 누르면
 * 403이 나는 화면이 생긴다.
 */
final class AlbumPermission {

    private AlbumPermission() {}

    /**
     * 올린 사람이거나 관리자({@link Role#ADMIN})면 지울 수 있다. 관리자는 PRODUCT.md 4절의 "다른 사람의 역할을 바꿀 수 있는 사람"이고 운영진
     * ({@link Role#STAFF})은 포함하지 않는다.
     *
     * <p>비로그인({@code null})과 승인 대기 회원은 false다. 삭제 API는 SecurityConfig 기본값(동아리원 이상)이 먼저 막으므로, 역할이 승인
     * 대기로 내려간 작성자에게 버튼만 보이는 일이 없게 여기서도 같은 조건을 건다 (auth 3-1-1 "승인 대기 회원").
     */
    static boolean canDelete(Album album, LoginUser user) {
        if (user == null || !user.hasRoleAtLeast(Role.MEMBER)) {
            return false;
        }
        return album.getUploaderId() == user.id() || user.isAdmin();
    }
}
