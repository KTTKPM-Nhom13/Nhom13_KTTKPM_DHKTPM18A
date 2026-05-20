package com.cab.ride.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Cấu hình Kafka Producer cho ride-service.
 * Mirror pattern từ booking-service (Jackson 3 / tools.jackson).
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>();

        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Tăng độ tin cậy: đảm bảo tất cả replica đã nhận message trước khi ack
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        configProps.put("spring.json.type.mapping",
                "ride-arrived:com.cab.ride.core.dto.event.outbound.RideArrivedEvent,"
                        + "ride-started:com.cab.ride.core.dto.event.outbound.RideStartedEvent,"
                        + "ride-completed:com.cab.ride.core.dto.event.outbound.RideCompletedEvent,"
                        + "ride-finished:com.cab.ride.core.dto.event.outbound.RideCompletedEvent,"
                        + "driver-location:com.cab.ride.core.dto.event.DriverLocationEvent");

        return new DefaultKafkaProducerFactory<>(
                configProps,
                new StringSerializer(),
                serializer
        );
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
