package com.hackhu.hp.global.auth;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContext;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

/**
 * 테스트 메서드나 클래스를 로그인 사용자로 실행한다. MockMvc 요청과 {@code @PreAuthorize}가 붙은 서비스 호출에 모두 적용된다.
 *
 * <pre>{@code
 * @Test
 * @WithLoginUser(id = 7, role = Role.MEMBER)
 * void 동아리원은_글을_쓴다() { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WithSecurityContext(factory = WithLoginUser.Factory.class)
public @interface WithLoginUser {

    long id() default 1L;

    Role role() default Role.MEMBER;

    final class Factory implements WithSecurityContextFactory<WithLoginUser> {

        @Override
        public SecurityContext createSecurityContext(WithLoginUser annotation) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(
                    new LoginUserAuthentication(new LoginUser(annotation.id(), annotation.role())));
            return context;
        }
    }
}
