package com.hackhu.hp.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiAccessDeniedHandlerTest {

    @Test
    void 로그_값의_줄바꿈과_제어_문자를_바꾼다() {
        // 줄바꿈으로 가짜 로그 줄을 만들거나 터미널 제어 문자를 넣는 경우 (CWE-117)
        String forged = "/api/v1/x\r\nINFO forged userId=999\u001b[31m\t";

        assertThat(ApiAccessDeniedHandler.forLog(forged))
                .isEqualTo("/api/v1/x__INFO forged userId=999_[31m_")
                .doesNotContain("\r", "\n", "\u001b", "\t");
    }

    @Test
    void 출력_가능한_ASCII가_아닌_문자도_바꾼다() {
        assertThat(ApiAccessDeniedHandler.forLog("/관리 ")).isEqualTo("/___");
    }

    @Test
    void 긴_값은_200자로_자른다() {
        assertThat(ApiAccessDeniedHandler.forLog("/" + "a".repeat(500))).hasSize(200);
    }

    @Test
    void 일반_경로는_그대로_둔다() {
        assertThat(ApiAccessDeniedHandler.forLog("/api/v1/admin/users/2/role?x=%0A"))
                .isEqualTo("/api/v1/admin/users/2/role?x=%0A");
        assertThat(ApiAccessDeniedHandler.forLog(null)).isNull();
    }
}
