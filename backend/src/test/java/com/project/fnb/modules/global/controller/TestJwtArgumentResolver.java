package com.project.fnb.modules.global.controller;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Custom argument resolver for tests that injects a mock JWT
 * into controller methods annotated with @AuthenticationPrincipal.
 */
public class TestJwtArgumentResolver implements HandlerMethodArgumentResolver {

    private final Jwt jwt;

    public TestJwtArgumentResolver(Jwt jwt) {
        this.jwt = jwt;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                && Jwt.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return jwt;
    }
}
