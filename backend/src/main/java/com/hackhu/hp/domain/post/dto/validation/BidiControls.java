package com.hackhu.hp.domain.post.dto.validation;

/** 양방향 텍스트 제어 문자. 보이는 글자의 방향을 뒤집어 다른 글이나 다른 파일 이름처럼 보이게 할 수 있다 (5-1-5). */
final class BidiControls {

    private BidiControls() {}

    /** U+202A~U+202E(임베딩, 오버라이드), U+2066~U+2069(격리). */
    static boolean isBidiControl(int codePoint) {
        return (codePoint >= 0x202A && codePoint <= 0x202E)
                || (codePoint >= 0x2066 && codePoint <= 0x2069);
    }
}
