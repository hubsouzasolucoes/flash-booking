package com.samuel.flashbooking.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samuel.flashbooking.application.event.EventAvailability;
import com.samuel.flashbooking.application.event.EventUseCases;
import com.samuel.flashbooking.infrastructure.persistence.adapter.PostgresEventRepository;
import com.samuel.flashbooking.infrastructure.persistence.repository.JpaEventRepository;
import com.samuel.flashbooking.infrastructure.persistence.repository.JpaOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({EventUseCases.class, PostgresEventRepository.class, JpaDomainEventOutbox.class,
        TransactionalOutboxIT.Dependencies.class})
class TransactionalOutboxIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired EventUseCases useCases;
    @Autowired JpaEventRepository events;
    @Autowired JpaOutboxRepository outbox;
    @Autowired TransactionTemplate transactions;

    @Test
    void commitsBusinessStateAndOutboxTogether() {
        transactions.executeWithoutResult(status -> useCases.create("Conference",
                Instant.parse("2027-10-01T18:00:00Z"), 100));
        assertThat(events.count()).isOne();
        assertThat(outbox.count()).isOne();
    }

    @Test
    void rollsBackBusinessStateAndOutboxTogether() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            useCases.create("Conference", Instant.parse("2027-10-01T18:00:00Z"), 100);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(events.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @TestConfiguration
    static class Dependencies {
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean EventAvailability availability() { return id -> Optional.empty(); }
    }
}
