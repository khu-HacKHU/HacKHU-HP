package com.hackhu.hp.global.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * 모든 요청이 거치는 인증·권한 설정 (docs/spec/auth/backend/5-1-QUALITY-SECURITY.md).
 *
 * <p>기본은 거부다. 아래 공개 목록에 없는 API는 동아리원({@code MEMBER}) 이상만 부를 수 있다. 승인 대기 회원({@code PENDING})은 방문자와
 * 같으므로 로그인했다는 것만으로 통과시키지 않는다 (5-1-1).
 *
 * <p>여기서는 경로 단위의 최소 수준만 건다. 작성자 확인 같은 리소스별 검사는 각 API가 한다 (CWE-639).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

    // 역할 이름. 순서는 PENDING < MEMBER < STAFF < ADMIN이고 높은 역할은 낮은 역할을 포함한다 (5-1-1).
    static final String PENDING = "PENDING";
    static final String MEMBER = "MEMBER";
    static final String STAFF = "STAFF";
    static final String ADMIN = "ADMIN";

    // 헬스체크와 API 문서. API 문서는 local 프로파일에서만 켜진다 (application.yml).
    // /error는 서블릿 오류 응답 경로라 막으면 원래 오류가 401로 바뀌어 보인다.
    private static final String[] INFRA_PATHS = {
        "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error"
    };

    // 공개 API. 명세에 "공개"라고 적힌 API만 메서드별로 넣는다 (5-1-1, api.md 4절).
    // 갤러리 조회는 gallery/backend/4-2 1, 2절에 "공개"로 적혀 있다.
    // *는 한 세그먼트만 맞으므로 /api/v1/albums/12는 열리고 그 아래 경로는 열리지 않는다.
    private static final String[] PUBLIC_GET_PATHS = {"/api/v1/albums", "/api/v1/albums/*"};

    // refresh와 로그아웃은 access 토큰 대신 refresh 쿠키로 확인하므로 여기 둔다.
    // 로그인, refresh, 로그아웃의 Origin 허용 목록 검사는 그 API가 한다 (5-1-5).
    private static final String[] PUBLIC_POST_PATHS = {
        "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) {
        SecurityErrorResponseWriter errorWriter = new SecurityErrorResponseWriter(objectMapper);

        http
                // access 토큰은 Authorization 헤더로만 받으므로 쿠키 기반 CSRF 토큰이 필요 없다 (5-1-5).
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // 서버 세션을 만들지 않는다. 로그인 상태는 토큰으로만 확인한다.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(INFRA_PATHS)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                                        .permitAll()
                                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS)
                                        .permitAll()
                                        // "로그인" 수준. 승인 대기 회원도 부르는 내 정보 API 세 개에만 쓴다 (5-1-1).
                                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me")
                                        .hasRole(PENDING)
                                        .requestMatchers(
                                                HttpMethod.PUT, "/api/v1/users/me/password")
                                        .hasRole(PENDING)
                                        .requestMatchers(HttpMethod.DELETE, "/api/v1/users/me")
                                        .hasRole(PENDING)
                                        .requestMatchers(
                                                HttpMethod.PUT, "/api/v1/admin/users/*/role")
                                        .hasRole(ADMIN)
                                        .requestMatchers("/api/v1/admin/**")
                                        .hasRole(STAFF)
                                        .anyRequest()
                                        .hasRole(MEMBER))
                // 인증 없음은 401, 역할 부족은 403을 공통 에러 형식으로 준다 (api.md 3절).
                .exceptionHandling(
                        e ->
                                e.authenticationEntryPoint(
                                                new ApiAuthenticationEntryPoint(errorWriter))
                                        .accessDeniedHandler(
                                                new ApiAccessDeniedHandler(errorWriter)));
        return http.build();
    }

    // URL 규칙과 @PreAuthorize가 함께 쓰는 역할 순서.
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(ADMIN)
                .implies(STAFF)
                .role(STAFF)
                .implies(MEMBER)
                .role(MEMBER)
                .implies(PENDING)
                .build();
    }

    // 프론트엔드 주소만 허용한다. *를 쓰지 않는다. 메서드와 헤더도 5-1-5에 적힌 것만 연다.
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // 생성 API의 Location, 요청 제한의 Retry-After를 프론트엔드가 읽는다 (api.md 1절, 4-2-1).
        config.setExposedHeaders(List.of("Location", "Retry-After"));
        // 프론트엔드가 모든 요청에 credentials: 'include'를 붙인다 (4-2-1).
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
