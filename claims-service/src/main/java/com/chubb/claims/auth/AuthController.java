package com.chubb.claims.auth;

import com.chubb.claims.domain.Actor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** DEV ONLY. Username-only login so the demo can switch between claimant/handler/manager. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final DevUserDirectory users;

    public AuthController(DevUserDirectory users) {
        this.users = users;
    }

    public record LoginRequest(String username) {
    }

    public record LoginResponse(String token, Actor user) {
    }

    public record DemoUser(String username, Actor user) {
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        Actor actor = users.byUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
        return new LoginResponse(actor.id().toString(), actor);
    }

    /** Lets the login screen list demo accounts. */
    @GetMapping("/users")
    public List<DemoUser> users() {
        return users.all().entrySet().stream()
                .map(e -> new DemoUser(e.getKey(), e.getValue()))
                .toList();
    }
}
