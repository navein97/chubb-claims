package com.chubb.claims.outbox;

import com.chubb.claims.app.ClaimEventRecorder;
import com.chubb.claims.domain.Claim;
import com.chubb.claims.events.ClaimEvent;
import com.chubb.claims.events.ClaimEventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Writes events to the outbox table. MANDATORY propagation: refuses to run outside the
 * command's transaction, so an event can never be recorded without its state change (or vice versa).
 */
@Component
class OutboxEventRecorder implements ClaimEventRecorder {

    private final OutboxRepository outbox;
    private final ObjectMapper json;

    OutboxEventRecorder(OutboxRepository outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Claim claim, List<ClaimEventType> events) {
        Instant now = Instant.now();
        for (ClaimEventType type : events) {
            ClaimEvent event = snapshot(claim, type, now);
            outbox.save(new OutboxEvent(event.eventId(), claim.getId(), type.name(), toJson(event), now));
        }
    }

    static ClaimEvent snapshot(Claim c, ClaimEventType type, Instant at) {
        return new ClaimEvent(
                UUID.randomUUID(), type, at,
                c.getId(), c.getReference(), c.getVersion(),
                c.getMarket().name(), c.getType().name(), c.getTeam(), c.getStatus().name(),
                c.getAssignedHandlerId(), c.getAssignedHandlerName(),
                c.getReserveAmount(), c.getSettledAmount(), c.getCurrency(),
                c.getSubmittedAt());
    }

    private String toJson(ClaimEvent event) {
        try {
            return json.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise event " + event.type(), e);
        }
    }
}
