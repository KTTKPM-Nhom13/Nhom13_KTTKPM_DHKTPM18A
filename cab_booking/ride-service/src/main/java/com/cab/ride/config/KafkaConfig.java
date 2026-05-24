package com.cab.ride.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka configuration for ride-service.
 *
 * <p><b>Producer</b>: auto-configured by Spring Boot from {@code application.yaml}
 * using {@code JsonSerializer} — sets {@code __TypeId__} headers so downstream
 * consumers (booking-service, driver-service, notification-service) can resolve
 * the DTO type via their {@code JsonDeserializer} type mappings.</p>
 *
 * <p>Previously used {@code JacksonJsonSerializer} which did NOT set type headers,
 * causing silent deserialization failures on consumers that rely on
 * {@code spring.json.use.type.headers=true}.</p>
 */
@Configuration
public class KafkaConfig {

    // ProducerFactory and KafkaTemplate are now auto-configured by Spring Boot
    // from application.yaml (JsonSerializer with type mapping + __TypeId__ headers).
}
