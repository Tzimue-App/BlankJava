package com.blankjava.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * Puts the real access rules in a {@code @WebMvcTest} slice (which does not scan them): security
 * filter chain, JWT keys (ephemeral, see src/test/resources/config/application.yaml), Problem
 * Details handlers, and {@link AccessTokenService} to issue valid tokens.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import({SecurityConfig.class, JwtConfig.class, ProblemSecurityHandlers.class, AccessTokenService.class})
public @interface ApiSecurity {}
