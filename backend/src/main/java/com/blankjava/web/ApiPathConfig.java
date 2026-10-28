package com.blankjava.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves every application controller under {@code /api}, the server URL of api/openapi.yaml. The
 * generated interfaces carry the paths relative to it ({@code /client-logs}); actuator endpoints
 * stay at {@code /actuator}.
 */
@Configuration
public class ApiPathConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forBasePackage("com.blankjava"));
    }
}
