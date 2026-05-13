package iuh.fit.pricing_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_RIDE_CREATED = "ride.created";
    public static final String TOPIC_PRICING_SURGE_UPDATED = "pricing.surge.updated";

    @Bean
    public NewTopic rideCreatedTopic() {
        return TopicBuilder.name(TOPIC_RIDE_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic surgeUpdatedTopic() {
        return TopicBuilder.name(TOPIC_PRICING_SURGE_UPDATED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
