package com.samuel.flashbooking.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {
    @Bean
    NewTopic domainEvents(@Value("${app.kafka.events-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic deadLetters(@Value("${app.kafka.events-topic}") String topic) {
        return TopicBuilder.name(topic + ".dlt").partitions(6).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> operations,
            @Value("${app.kafka.events-topic}") String topic) {
        var recoverer = new DeadLetterPublishingRecoverer(operations,
                (record, exception) -> new TopicPartition(topic + ".dlt", record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000, 2));
    }
}
