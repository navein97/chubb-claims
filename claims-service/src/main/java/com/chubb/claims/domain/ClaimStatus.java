package com.chubb.claims.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Claim lifecycle. The allowed-transition table is the single source of truth:
 * every state change in {@link Claim} goes through {@link #canMoveTo}.
 *
 * SUBMITTED -> ASSIGNED -> UNDER_ASSESSMENT <-> AWAITING_INFO
 * UNDER_ASSESSMENT -> APPROVED -> SETTLED
 * UNDER_ASSESSMENT -> REJECTED
 */
public enum ClaimStatus {
    SUBMITTED,
    ASSIGNED,
    UNDER_ASSESSMENT,
    AWAITING_INFO,
    APPROVED,
    REJECTED,
    SETTLED;

    private static final Map<ClaimStatus, Set<ClaimStatus>> ALLOWED = Map.of(
            SUBMITTED, EnumSet.of(ASSIGNED),
            ASSIGNED, EnumSet.of(UNDER_ASSESSMENT),
            UNDER_ASSESSMENT, EnumSet.of(AWAITING_INFO, APPROVED, REJECTED),
            AWAITING_INFO, EnumSet.of(UNDER_ASSESSMENT),
            APPROVED, EnumSet.of(SETTLED),
            REJECTED, EnumSet.noneOf(ClaimStatus.class),
            SETTLED, EnumSet.noneOf(ClaimStatus.class)
    );

    public boolean canMoveTo(ClaimStatus target) {
        return ALLOWED.get(this).contains(target);
    }

    /** Open = still carries liability. Reserve on open claims is outstanding exposure. */
    public boolean isOpen() {
        return this != REJECTED && this != SETTLED;
    }
}
