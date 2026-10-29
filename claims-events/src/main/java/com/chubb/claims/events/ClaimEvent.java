package com.chubb.claims.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Single envelope published to the {@code claims.events} topic, keyed by claimId.
 *
 * Event-carried state transfer: every event carries the claim's current snapshot
 * (status, reserve, handler, version). Consumers never call back into claims-service,
 * and can rebuild any projection by replaying the topic.
 *
 * {@code claimVersion} lets consumers drop stale or duplicate deliveries:
 * apply only if claimVersion > the version already stored.
 */
public record ClaimEvent(
        UUID eventId,
        ClaimEventType type,
        Instant occurredAt,
        UUID claimId,
        String claimRef,
        long claimVersion,
        String market,
        String claimType,
        String status,
        UUID handlerId,
        String team,
        BigDecimal reserveAmount,
        String currency,
        BigDecimal settledAmount
) {
    public static final String TOPIC = "claims.events";
}
