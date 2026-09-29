package com.samuel.flashbooking.infrastructure.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI api() {
        return new OpenAPI().info(new Info().title("Flash Booking API").version("1.0.0")
                .description("API local de reservas com controle atômico de capacidade, idempotência persistente e Outbox/Kafka."));
    }
}
