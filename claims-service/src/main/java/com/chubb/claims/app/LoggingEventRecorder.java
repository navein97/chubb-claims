package com.chubb.claims.app;

import com.chubb.claims.domain.Claim;
import com.chubb.claims.events.ClaimEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** Placeholder until the outbox lands. Logs what would be published. */
@Component
class LoggingEventRecorder implements ClaimEventRecorder {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventRecorder.class);

    @Override
    public void record(Claim claim, List<ClaimEventType> events) {
        events.forEach(e -> log.info("event {} claim={} v{}", e, claim.getReference(), claim.getVersion()));
    }
}
