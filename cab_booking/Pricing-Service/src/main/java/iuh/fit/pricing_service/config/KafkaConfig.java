package iuh.fit.pricing_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {


    public static final String TOPIC_PRICING_SURGE_UPDATED = "pricing.surge.updated";
    public static final String TOPIC_PRICING_ESTIMATE_CREATED = "pricing.estimate.created";
    public static final String TOPIC_PRICING_ESTIMATE_CONFIRMED = "pricing.estimate.confirmed";
    public static final String TOPIC_PRICING_ESTIMATE_EXPIRED = "pricing.estimate.expired";
    public static final String TOPIC_PRICING_CONFIG_UPDATED = "pricing.config.updated";


    @Bean
    public NewTopic surgeUpdatedTopic() {
        return TopicBuilder.name(TOPIC_PRICING_SURGE_UPDATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic estimateCreatedTopic() {
        return TopicBuilder.name(TOPIC_PRICING_ESTIMATE_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic estimateConfirmedTopic() {
        return TopicBuilder.name(TOPIC_PRICING_ESTIMATE_CONFIRMED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic estimateExpiredTopic() {
        return TopicBuilder.name(TOPIC_PRICING_ESTIMATE_EXPIRED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic pricingConfigUpdatedTopic() {
        return TopicBuilder.name(TOPIC_PRICING_CONFIG_UPDATED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
