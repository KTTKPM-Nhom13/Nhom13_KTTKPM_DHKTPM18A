package com.cab.booking.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        // 1. Tạo ObjectMapper theo chuẩn Jackson 3
        ObjectMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .build();

        // 2. Khởi tạo Serializer và thiết lập ObjectMapper
        // Nếu constructor (mapper) báo lỗi, hãy dùng constructor mặc định rồi set sau
        JacksonJsonSerializer<Object> serializer = new JacksonJsonSerializer<>();
        // Sử dụng phương thức set để gán mapper (đảm bảo tương thích mọi phiên bản)
        // Lưu ý: Package tools.jackson.databind.ObjectMapper phải khớp
        // serializer.setObjectMapper(mapper);

        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // 3. Trả về Factory với Serializer đã cấu hình
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