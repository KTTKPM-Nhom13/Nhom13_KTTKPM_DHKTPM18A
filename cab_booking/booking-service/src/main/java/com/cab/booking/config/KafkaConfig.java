package com.cab.booking.config;

import com.cab.booking.core.dto.event.DriverStatusEvent;
import com.cab.booking.core.dto.event.inbound.DriverArrivedEvent;
import com.cab.booking.core.dto.event.inbound.PaymentCompletedEvent;
import com.cab.booking.core.dto.event.inbound.PaymentFailedEvent;
import com.cab.booking.core.dto.event.inbound.RideAssignedEvent;
import com.cab.booking.core.dto.event.inbound.RideFinishedEvent;
import com.cab.booking.core.dto.event.inbound.RideStartedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:booking-service-group}")
    private String consumerGroupId;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    private <T> ConsumerFactory<String, T> jsonConsumerFactory(Class<T> targetType) {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        JsonDeserializer<T> jsonDeserializer = new JsonDeserializer<>(targetType);
        jsonDeserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(configProps, new StringDeserializer(), jsonDeserializer);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> jsonKafkaListenerContainerFactory(Class<T> targetType) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(jsonConsumerFactory(targetType));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RideAssignedEvent> rideAssignedKafkaListenerContainerFactory() {
        return jsonKafkaListenerContainerFactory(RideAssignedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DriverStatusEvent> driverStatusKafkaListenerContainerFactory() {
        return jsonKafkaListenerContainerFactory(DriverStatusEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DriverArrivedEvent> driverArrivedKafkaListenerContainerFactory() {
        return jsonKafkaListenerContainerFactory(DriverArrivedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RideStartedEvent> rideStartedKafkaListenerContainerFactory() {
        return jsonKafkaListenerContainerFactory(RideStartedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RideFinishedEvent> rideFinishedKafkaListenerContainerFactory() {
        return jsonKafkaListenerContainerFactory(RideFinishedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentCompletedEvent> paymentCompletedKafkaListenerContainerFactory() {
        return jsonKafkaListenerContainerFactory(PaymentCompletedEvent.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentFailedEvent> paymentFailedKafkaListenerContainerFactory() {
        return jsonKafkaListenerContainerFactory(PaymentFailedEvent.class);
    }
}
