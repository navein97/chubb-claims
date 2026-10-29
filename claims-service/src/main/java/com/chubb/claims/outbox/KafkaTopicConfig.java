package com.chubb.claims.outbox;

import com.chubb.claims.events.ClaimEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
class KafkaTopicConfig {

    /** 3 partitions: per-claim ordering via the claimId key, parallelism across claims. */
    @Bean
    NewTopic claimsEventsTopic() {
        return TopicBuilder.name(ClaimEvent.TOPIC).partitions(3).replicas(1).build();
    }
}
