package com.cab.matching.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_BOOKING_CREATED = "booking.created";
    public static final String TOPIC_RIDE_CREATED_LEGACY = "ride.created";
    public static final String TOPIC_DRIVER_ASSIGNED = "driver.assigned";
    public static final String TOPIC_RIDE_ASSIGNED_LEGACY = "ride.assigned";

    @Bean
    public NewTopic bookingCreatedTopic() {
        return TopicBuilder.name(TOPIC_BOOKING_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic driverAssignedTopic() {
        return TopicBuilder.name(TOPIC_DRIVER_ASSIGNED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rideAssignedLegacyTopic() {
        return TopicBuilder.name(TOPIC_RIDE_ASSIGNED_LEGACY)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
