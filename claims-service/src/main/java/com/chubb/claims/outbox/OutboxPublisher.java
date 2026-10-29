package com.chubb.claims.outbox;

import com.chubb.claims.events.ClaimEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Relays outbox rows to Kafka.
 *
 * Delivery is at-least-once: if the process dies after Kafka acks but before the row is marked
 * published, the event is sent again. Consumers handle that with the claimVersion check.
 *
 * On the first failure in a batch we stop, so later events for the same claim are never
 * published ahead of an earlier one that failed.
 */
@Component
class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;

    OutboxPublisher(OutboxRepository outbox, KafkaTemplate<String, String> kafka,
                    @Value("${claims.outbox.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${claims.outbox.poll-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outbox.lockNextBatch(batchSize);
        for (OutboxEvent row : batch) {
            try {
                kafka.send(ClaimEvent.TOPIC, row.getAggregateId().toString(), row.getPayload())
                        .get(5, TimeUnit.SECONDS);
                row.markPublished(Instant.now());
            } catch (Exception e) {
                row.markFailedAttempt();
                log.warn("Outbox publish failed for event {} (attempt {}): {}",
                        row.getId(), row.getAttempts(), e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }
    }
}
