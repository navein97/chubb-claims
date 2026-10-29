package com.chubb.claims.auth;

import com.chubb.claims.domain.Actor;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets controllers declare an {@link Actor} parameter instead of touching request internals. */
public class ActorArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(Actor.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        return request.getAttribute(DevAuthFilter.ACTOR_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    }
}
