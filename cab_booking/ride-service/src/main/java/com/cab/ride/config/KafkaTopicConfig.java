package com.cab.ride.config;

import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-creates Kafka topics nếu chưa tồn tại khi service khởi động.
 */
@Configuration
public class KafkaTopicConfig {

    /** Topic nhận vị trí GPS từ tài xế — high-throughput, 3 partitions. */
    @Bean
    public KafkaAdmin.NewTopics rideServiceTopics() {
        return new KafkaAdmin.NewTopics(
                TopicBuilder.name("driver.location.updated")
                        .partitions(3)
                        .replicas(1)
                        .build(),
                TopicBuilder.name("ride.assigned")
                        .partitions(1)
                        .replicas(1)
                        .build(),
                TopicBuilder.name("payment.completed")
                        .partitions(1)
                        .replicas(1)
                        .build()
        );
    }
}
