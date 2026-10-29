package com.chubb.claims.auth;

import com.chubb.claims.domain.Actor;
import com.chubb.claims.domain.Role;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * DEV ONLY. Seeded users standing in for a real identity provider.
 * In production: OIDC (Azure AD for staff, a customer IdP for claimants), and the Actor
 * would be built from validated JWT claims (sub, role, team). Nothing else in the code changes.
 */
@Component
public class DevUserDirectory {

    private final Map<String, Actor> byUsername = new LinkedHashMap<>();
    private final Map<UUID, Actor> byId = new LinkedHashMap<>();

    public DevUserDirectory() {
        add("aisha", "Aisha Rahman", Role.CLAIMANT, null, "00000000-0000-0000-0000-00000000c001");
        add("ben", "Ben Tan", Role.CLAIMANT, null, "00000000-0000-0000-0000-00000000c002");
        add("huimin", "Lim Hui Min", Role.HANDLER, "MY-MOTOR", "00000000-0000-0000-0000-00000000a001");
        add("raj", "Raj Kumar", Role.HANDLER, "MY-MOTOR", "00000000-0000-0000-0000-00000000a002");
        add("wei", "Wei Ling", Role.HANDLER, "SG-PROPERTY", "00000000-0000-0000-0000-00000000a003");
        add("grace", "Grace Wong", Role.MANAGER, "MY-MOTOR", "00000000-0000-0000-0000-00000000b001");
    }

    private void add(String username, String name, Role role, String team, String id) {
        Actor actor = new Actor(UUID.fromString(id), name, role, team);
        byUsername.put(username, actor);
        byId.put(actor.id(), actor);
    }

    public Optional<Actor> byUsername(String username) {
        return Optional.ofNullable(byUsername.get(username == null ? null : username.toLowerCase()));
    }

    public Optional<Actor> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Map<String, Actor> all() {
        return byUsername;
    }

    public Collection<Actor> actors() {
        return byId.values();
    }
}
