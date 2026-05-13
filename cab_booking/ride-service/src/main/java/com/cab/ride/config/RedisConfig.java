package com.cab.ride.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cấu hình RedisTemplate cho ride-service.
 * Mirror cấu hình của booking-service để đảm bảo tính nhất quán trong toàn hệ thống.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // ObjectMapper theo chuẩn Jackson 3 (tools.jackson)
        ObjectMapper mapper = JsonMapper.builder()
                .findAndAddModules() // Tự động nhận diện JavaTimeModule, v.v.
                .build();

        GenericJacksonJsonRedisSerializer serializer = new GenericJacksonJsonRedisSerializer(mapper);

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
