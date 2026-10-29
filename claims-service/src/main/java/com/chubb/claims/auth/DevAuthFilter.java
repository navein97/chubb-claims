package com.chubb.claims.auth;

import com.chubb.claims.domain.Actor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * DEV ONLY. Resolves "Authorization: Bearer &lt;userId&gt;" to an {@link Actor}.
 * Swap for Spring Security's OAuth2 resource server + JWT validation in production.
 */
@Component
public class DevAuthFilter extends OncePerRequestFilter {

    public static final String ACTOR_ATTRIBUTE = "claims.actor";

    private final DevUserDirectory users;

    public DevAuthFilter(DevUserDirectory users) {
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Actor> actor = resolve(request.getHeader("Authorization"));
        if (actor.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Missing or invalid token\"}");
            return;
        }
        request.setAttribute(ACTOR_ATTRIBUTE, actor.get());
        chain.doFilter(request, response);
    }

    private Optional<Actor> resolve(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        try {
            return users.byId(UUID.fromString(header.substring(7).trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
