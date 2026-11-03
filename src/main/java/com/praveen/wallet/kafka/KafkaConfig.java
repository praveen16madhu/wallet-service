package com.praveen.wallet.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "wallet.kafka.enabled", havingValue = "true")
public class KafkaConfig {

    public static final String TOPIC = "wallet.transfers";
    public static final String DLT = "wallet.transfers.DLT";

    @Bean
    NewTopic transfersTopic() {
        return TopicBuilder.name(TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic transfersDlt() {
        return TopicBuilder.name(DLT).partitions(1).replicas(1).build();
    }

    /**
     * A message that can never succeed (malformed) goes straight to the dead-letter topic.
     * A transient failure is retried twice, then also parked, so one bad message never blocks the partition.
     */
    @Bean
    CommonErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(DLT, 0));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(200L, 2L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
