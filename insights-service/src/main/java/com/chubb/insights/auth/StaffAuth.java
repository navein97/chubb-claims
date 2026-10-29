package com.chubb.insights.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DEV ONLY. Resolves "Authorization: Bearer &lt;userId&gt;" to a staff member.
 * Insights is staff-only: claimants get 403. Production: validate the IdP's JWT here,
 * independently of claims-service (no service-to-service call to check a token).
 */
@Component
@ConfigurationProperties(prefix = "dev")
public class StaffAuth {

    public record Staff(UUID id, String name, String role, String team) {
        public boolean isManager() {
            return "MANAGER".equals(role);
        }
    }

    private List<Staff> staff = List.of();

    public void setStaff(List<Staff> staff) {
        this.staff = staff;
    }

    public Staff require(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing token");
        }
        return find(authorization.substring(7).trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff only"));
    }

    private Optional<Staff> find(String id) {
        return staff.stream().filter(s -> s.id().toString().equals(id)).findFirst();
    }
}
