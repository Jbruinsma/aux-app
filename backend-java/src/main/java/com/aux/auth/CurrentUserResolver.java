package com.aux.auth;

import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.aux.entity.UserEntity;
import com.aux.error.AuxException;
import com.aux.repository.UserRepository;
import com.aux.services.SessionService;
import io.jsonwebtoken.JwtException;

@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

    private final SessionService sessions;
    private final UserRepository users;

    public CurrentUserResolver(SessionService sessions, UserRepository users) {
        this.sessions = sessions;
        this.users = users;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && parameter.getParameterType() == UserEntity.class;
    }

    @Override
    public UserEntity resolveArgument(@NonNull MethodParameter parameter, ModelAndViewContainer mav,
                                      NativeWebRequest request, WebDataBinderFactory binder) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            throw unauthorized();
        }
        try {
            String userId = sessions.verify(header.substring("Bearer ".length()));
            // The account may have been deleted after the token was issued
            return users.findById(userId).orElseThrow(this::unauthorized);
        } catch (JwtException | IllegalArgumentException e) {
            throw unauthorized();
        }
    }

    private AuxException unauthorized() {
        return new AuxException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Missing or invalid token");
    }
}
