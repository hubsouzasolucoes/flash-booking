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
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class KafkaConfiguration {
    private static final Logger log = LoggerFactory.getLogger(KafkaConfiguration.class);
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
            @Value("${app.kafka.events-topic}") String topic, MeterRegistry metrics) {
        var recoverer = new DeadLetterPublishingRecoverer(operations,
                (record, exception) -> new TopicPartition(topic + ".dlt", record.partition()));
        var dlt = metrics.counter("booking.consumer.dlt", "consumer", "availability-projection-v1");
        var failed = metrics.counter("booking.consumer.retry", "consumer", "availability-projection-v1");
        var handler = new DefaultErrorHandler((record, exception) -> {
            recoverer.accept(record, exception);
            dlt.increment();
            log.warn("event=consumer_dlt topic={} partition={} offset={} exception={}",
                    record.topic(), record.partition(), record.offset(), exception.getClass().getSimpleName());
        }, new FixedBackOff(1_000, 2));
        handler.setRetryListeners((record, exception, attempt) -> {
            failed.increment();
            log.warn("event=consumer_retry topic={} partition={} offset={} attempt={} exception={}",
                    record.topic(), record.partition(), record.offset(), attempt, exception.getClass().getSimpleName());
        });
        return handler;
    }
}
