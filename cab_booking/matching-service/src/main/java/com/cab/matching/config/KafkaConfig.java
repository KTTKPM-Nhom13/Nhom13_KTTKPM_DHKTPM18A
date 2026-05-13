package com.cab.matching.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_RIDE_CREATED = "ride.created";
    public static final String TOPIC_DRIVER_MATCHED = "driver.matched";

    @Bean
    public NewTopic driverMatchedTopic() {
        return TopicBuilder.name(TOPIC_DRIVER_MATCHED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
