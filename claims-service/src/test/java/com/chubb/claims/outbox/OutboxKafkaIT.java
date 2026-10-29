package com.chubb.claims.outbox;

import com.chubb.claims.IntegrationTest;
import com.chubb.claims.app.ClaimService;
import com.chubb.claims.auth.DevUserDirectory;
import com.chubb.claims.domain.*;
import com.chubb.claims.events.ClaimEvent;
import com.chubb.claims.events.ClaimEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.EmbeddedKafkaBroker;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** Command → outbox row → poller → Kafka, against real Postgres and Kafka. */
class OutboxKafkaIT extends IntegrationTest {

    @Autowired
    ClaimService service;
    @Autowired
    DevUserDirectory users;
    @Autowired
    OutboxRepository outbox;
    @Autowired
    ObjectMapper json;
    @Autowired
    EmbeddedKafkaBroker broker;

    @Test
    void stateChangesArePublishedInOrder_keyedByClaim_withIncreasingVersions() throws Exception {
        Actor aisha = users.byUsername("aisha").orElseThrow();
        Actor huimin = users.byUsername("huimin").orElseThrow();

        Claim claim = service.submit(aisha, "POL-9", Market.MY, ClaimType.MOTOR,
                LocalDate.now().minusDays(1), "Flood damage to engine", new BigDecimal("12000.00"));
        service.pickUp(huimin, claim.getId());
        service.startAssessment(huimin, claim.getId());
        service.updateReserve(huimin, claim.getId(), new BigDecimal("9000.00"), "Assessor report");

        List<ClaimEvent> events = consumeFor(claim.getId(), 4);
        assertThat(outbox.countByPublishedAtIsNull()).isZero();

        assertThat(events).extracting(ClaimEvent::type).containsExactly(
                ClaimEventType.CLAIM_SUBMITTED, ClaimEventType.CLAIM_ASSIGNED,
                ClaimEventType.CLAIM_STATUS_CHANGED, ClaimEventType.RESERVE_UPDATED);
        assertThat(events).extracting(ClaimEvent::claimVersion).isSorted().doesNotHaveDuplicates();
        ClaimEvent last = events.get(3);
        assertThat(last.reserveAmount()).isEqualByComparingTo("9000.00");
        assertThat(last.handlerName()).isEqualTo("Lim Hui Min");
        assertThat(last.currency()).isEqualTo("MYR");
        assertThat(last.team()).isEqualTo("MY-MOTOR");
    }

    private List<ClaimEvent> consumeFor(UUID claimId, int expected) throws Exception {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        List<ClaimEvent> found = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(ClaimEvent.TOPIC));
            long deadline = System.currentTimeMillis() + 20_000;
            while (found.size() < expected && System.currentTimeMillis() < deadline) {
                for (ConsumerRecord<String, String> rec : consumer.poll(Duration.ofMillis(500))) {
                    if (rec.key().equals(claimId.toString())) {
                        found.add(json.readValue(rec.value(), ClaimEvent.class));
                    }
                }
            }
        }
        return found;
    }
}
