package com.hackhu.hp.global.storage;

/** 업로드 발급 기록이 어느 기능에서 왔는지. S3 키 접두사로도 쓴다 (예: gallery/{uuid}). */
public enum UploadPurpose {
    GALLERY,
    ATTACHMENT
}
