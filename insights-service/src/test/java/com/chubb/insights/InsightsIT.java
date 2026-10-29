package com.chubb.insights;

import com.chubb.claims.events.ClaimEvent;
import com.chubb.claims.events.ClaimEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = ClaimEvent.TOPIC, kraft = true)
@AutoConfigureMockMvc
class InsightsIT {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    static final String GRACE = "00000000-0000-0000-0000-00000000b001";
    static final String HUIMIN = "00000000-0000-0000-0000-00000000a001";
    static final String AISHA = "00000000-0000-0000-0000-00000000c001";

    @Autowired
    KafkaTemplate<String, String> kafka;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    MockMvc mvc;
    @Autowired
    EmbeddedKafkaBroker broker;

    @Test
    void projectionIsIdempotent_orderTolerant_andFeedsDashboards() throws Exception {
        // Empty projection still answers
        mvc.perform(get("/api/insights/exposure").header("Authorization", "Bearer " + GRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(0)));

        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID huimin = UUID.fromString(HUIMIN);

        // A: submitted → assigned, assigned delivered twice, then a stale v0 replay
        send(event(a, 0, ClaimEventType.CLAIM_SUBMITTED, "MY", "MOTOR", "SUBMITTED", null, "4500.00", "MYR"));
        send(event(a, 1, ClaimEventType.CLAIM_ASSIGNED, "MY", "MOTOR", "ASSIGNED", huimin, "4500.00", "MYR"));
        send(event(a, 1, ClaimEventType.CLAIM_ASSIGNED, "MY", "MOTOR", "ASSIGNED", huimin, "4500.00", "MYR"));
        send(event(a, 0, ClaimEventType.CLAIM_SUBMITTED, "MY", "MOTOR", "SUBMITTED", null, "4500.00", "MYR"));
        // B: rejected → closed, no longer exposure
        send(event(b, 0, ClaimEventType.CLAIM_SUBMITTED, "MY", "MOTOR", "SUBMITTED", null, "1000.00", "MYR"));
        send(event(b, 3, ClaimEventType.CLAIM_DECIDED, "MY", "MOTOR", "REJECTED", huimin, "0.00", "MYR"));
        // C: another market and currency
        send(event(c, 0, ClaimEventType.CLAIM_SUBMITTED, "SG", "PROPERTY", "SUBMITTED", null, "20000.00", "SGD"));
        // Poison message
        kafka.send(ClaimEvent.TOPIC, UUID.randomUUID().toString(), "{not json").get();

        awaitTrue(() -> projStatus(a).equals("ASSIGNED") && projStatus(b).equals("REJECTED") && projStatus(c).equals("SUBMITTED"));
        Thread.sleep(1000); // let the stale replay of A arrive too
        assertThat(projStatus(a)).isEqualTo("ASSIGNED");

        // Manager: everything, per currency
        mvc.perform(get("/api/insights/exposure").header("Authorization", "Bearer " + GRACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf", notNullValue()))
                .andExpect(jsonPath("$.totals[?(@.currency=='MYR')].openClaims").value(contains(1)))
                .andExpect(jsonPath("$.totals[?(@.currency=='MYR')].outstandingReserve").value(contains(4500.00)))
                .andExpect(jsonPath("$.totals[?(@.currency=='SGD')].outstandingReserve").value(contains(20000.00)));

        // Handler: pinned to own team even if asking for another
        mvc.perform(get("/api/insights/exposure?team=SG-PROPERTY").header("Authorization", "Bearer " + HUIMIN))
                .andExpect(jsonPath("$.team").value("MY-MOTOR"))
                .andExpect(jsonPath("$.rows[*].market").value(everyItem(is("MY"))));

        // Workload
        mvc.perform(get("/api/insights/workload?team=MY-MOTOR").header("Authorization", "Bearer " + GRACE))
                .andExpect(jsonPath("$.teams[0].team").value("MY-MOTOR"))
                .andExpect(jsonPath("$.teams[0].inProgress").value(1))
                .andExpect(jsonPath("$.teams[0].closedLast30Days").value(1))
                .andExpect(jsonPath("$.handlers[0].handlerName").value("Lim Hui Min"))
                .andExpect(jsonPath("$.handlers[0].open").value(1))
                .andExpect(jsonPath("$.handlers[0].closedLast30Days").value(1));

        // Claimants and anonymous callers are kept out
        mvc.perform(get("/api/insights/workload").header("Authorization", "Bearer " + AISHA))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/insights/workload")).andExpect(status().isUnauthorized());

        // Poison message parked on the DLT instead of blocking the partition
        assertThat(countRecords(ClaimEvent.TOPIC + "-dlt")).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private ClaimEvent event(UUID id, long version, ClaimEventType type, String market, String claimType,
                             String status, UUID handler, String reserve, String currency) {
        Instant submitted = Instant.now().minus(Duration.ofDays(2));
        return new ClaimEvent(UUID.randomUUID(), type, Instant.now(), id, "CLM-" + market + "-" + version,
                version, market, claimType, market + "-" + claimType, status,
                handler, handler == null ? null : "Lim Hui Min",
                new BigDecimal(reserve), null, currency, submitted);
    }

    private void send(ClaimEvent e) throws Exception {
        kafka.send(ClaimEvent.TOPIC, e.claimId().toString(), json.writeValueAsString(e)).get();
    }

    private String projStatus(UUID id) {
        return jdbc.sql("SELECT status FROM claim_projection WHERE claim_id = :id")
                .param("id", id).query(String.class).optional().orElse("");
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met within 20s");
            }
            Thread.sleep(200);
        }
    }

    private long countRecords(String topic) throws InterruptedException {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        long count = 0;
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 15_000;
            while (System.currentTimeMillis() < deadline && count == 0) {
                count += consumer.poll(Duration.ofMillis(500)).count();
            }
        }
        return count;
    }
}
