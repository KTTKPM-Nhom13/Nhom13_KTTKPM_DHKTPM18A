package com.cab.matching.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_RIDE_CREATED = "ride.created";
    public static final String TOPIC_MATCHING_RETRY_REQUESTED = "matching.retry.requested";
    public static final String TOPIC_MATCHING_FAILED = "matching.failed";
    public static final String TOPIC_RIDE_ASSIGNED = "ride.assigned";

    @Bean
    public NewTopic rideCreatedTopic() {
        return TopicBuilder.name(TOPIC_RIDE_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic rideAssignedTopic() {
        return TopicBuilder.name(TOPIC_RIDE_ASSIGNED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic matchingRetryRequestedTopic() {
        return TopicBuilder.name(TOPIC_MATCHING_RETRY_REQUESTED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic matchingFailedTopic() {
        return TopicBuilder.name(TOPIC_MATCHING_FAILED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
