package com.chubb.claims;

import com.chubb.claims.events.ClaimEvent;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real Postgres (Testcontainers, started once for all test classes) and an in-process
 * Kafka broker (KRaft) from spring-kafka-test.
 *
 * Every IT shares this exact configuration so Spring caches ONE context. Two contexts would mean
 * two outbox pollers racing for the same outbox rows and publishing to two different brokers.
 */
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = ClaimEvent.TOPIC, kraft = true)
public abstract class IntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }
}
