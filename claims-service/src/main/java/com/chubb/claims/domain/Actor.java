package com.chubb.claims.domain;

import java.util.UUID;

/** Who is performing an action. Resolved from the request's auth token at the API edge. */
public record Actor(UUID id, String name, Role role, String team) {

    public boolean isStaff() {
        return role == Role.HANDLER || role == Role.MANAGER;
    }
}
