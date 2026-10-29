package com.chubb.claims.app;

import com.chubb.claims.domain.Claim;
import com.chubb.claims.events.ClaimEventType;

import java.util.List;

/**
 * Called inside the command's transaction with the events the aggregate produced.
 * Implemented by the outbox (stage 4), so events commit atomically with the state change.
 */
public interface ClaimEventRecorder {
    void record(Claim claim, List<ClaimEventType> events);
}
