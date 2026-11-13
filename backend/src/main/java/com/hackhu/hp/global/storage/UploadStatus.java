package com.hackhu.hp.global.storage;

/** 발급된 업로드 주소가 아직 확정에 쓰이지 않았는지(ISSUED), 이미 앨범·첨부로 확정됐는지(USED). */
public enum UploadStatus {
    ISSUED,
    USED
}
