package com.cab.booking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic rideCreatedTopic() {
        return TopicBuilder.name("ride.created").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideAssignedTopic() {
        return TopicBuilder.name("ride.assigned").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideAcceptedTopic() {
        return TopicBuilder.name("ride.accepted").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideRejectedTopic() {
        return TopicBuilder.name("ride.rejected").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideArrivedTopic() {
        return TopicBuilder.name("ride.arrived").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideStartedTopic() {
        return TopicBuilder.name("ride.started").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideCompletedTopic() {
        return TopicBuilder.name("ride.completed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic rideCancelledTopic() {
        return TopicBuilder.name("ride.cancelled").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name("payment.completed").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name("payment.failed").partitions(3).replicas(1).build();
    }
}
