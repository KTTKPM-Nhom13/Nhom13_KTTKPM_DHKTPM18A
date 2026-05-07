package com.cab.booking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    // Topic gửi đi khi tạo booking
    @Bean
    public NewTopic rideCreatedTopic() {
        return TopicBuilder.name("ride.created")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Topic nhận khi AI Matching gán driver
    @Bean
    public NewTopic rideAssignedTopic() {
        return TopicBuilder.name("ride.assigned")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Topic gửi đi khi driver hoàn thành chuyến → Payment Service
    @Bean
    public NewTopic rideFinishedTopic() {
        return TopicBuilder.name("ride.finished")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Topic nhận khi Payment Service thanh toán xong
    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name("payment.completed")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
