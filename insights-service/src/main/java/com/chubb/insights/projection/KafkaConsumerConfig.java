package com.chubb.insights.projection;

import com.chubb.claims.events.ClaimEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConsumerConfig {

    /**
     * Transient failures (e.g. DB blip): retry 3 times, 1s apart.
     * Anything still failing goes to claims.events-dlt so one bad message can't block the partition.
     * Unparseable JSON skips the retries — it will never succeed.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template), new FixedBackOff(1000L, 3));
        handler.addNotRetryableExceptions(JsonProcessingException.class);
        return handler;
    }

    /** Same partition count as the source: the recoverer keeps the original partition number. */
    @Bean
    NewTopic claimsEventsDlt() {
        return TopicBuilder.name(ClaimEvent.TOPIC + "-dlt").partitions(3).replicas(1).build();
    }
}
