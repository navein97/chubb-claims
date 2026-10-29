package com.chubb.insights.projection;

import com.chubb.claims.events.ClaimEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ClaimEventListener {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventListener.class);

    private final ClaimProjectionRepository projections;
    private final ObjectMapper json;

    public ClaimEventListener(ClaimProjectionRepository projections, ObjectMapper json) {
        this.projections = projections;
        this.json = json;
    }

    /**
     * Offsets commit after this returns (Spring's default AckMode.BATCH), so a crash mid-processing
     * redelivers rather than loses. Redelivery is harmless thanks to the version check in apply().
     * Malformed payloads are retried then sent to claims.events-dlt (see KafkaConsumerConfig).
     */
    @KafkaListener(topics = ClaimEvent.TOPIC)
    public void on(String payload) throws JsonProcessingException {
        ClaimEvent event = json.readValue(payload, ClaimEvent.class);
        boolean applied = projections.apply(event);
        if (!applied) {
            log.debug("Ignored stale/duplicate {} for {} v{}", event.type(), event.claimRef(), event.claimVersion());
        }
    }
}
