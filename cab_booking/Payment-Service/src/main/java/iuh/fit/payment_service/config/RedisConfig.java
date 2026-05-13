package iuh.fit.payment_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public IdempotencyRedisService idempotencyRedisService(RedisTemplate<String, Object> redisTemplate) {
        return new IdempotencyRedisService(redisTemplate);
    }

    public static class IdempotencyRedisService {

        private final RedisTemplate<String, Object> redisTemplate;
        private static final String IDEMPOTENCY_PREFIX = "payment:idempotency:";
        private static final Duration TTL = Duration.ofHours(24);

        public IdempotencyRedisService(RedisTemplate<String, Object> redisTemplate) {
            this.redisTemplate = redisTemplate;
        }

        public boolean exists(String idempotencyKey) {
            return Boolean.TRUE.equals(redisTemplate.hasKey(IDEMPOTENCY_PREFIX + idempotencyKey));
        }

        public void put(String idempotencyKey, String transactionId) {
            redisTemplate.opsForValue().set(IDEMPOTENCY_PREFIX + idempotencyKey, transactionId, TTL);
        }

        public String get(String idempotencyKey) {
            Object value = redisTemplate.opsForValue().get(IDEMPOTENCY_PREFIX + idempotencyKey);
            return value != null ? value.toString() : null;
        }

        public void delete(String idempotencyKey) {
            redisTemplate.delete(IDEMPOTENCY_PREFIX + idempotencyKey);
        }

        public boolean setIfAbsent(String idempotencyKey, String transactionId) {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(
                    IDEMPOTENCY_PREFIX + idempotencyKey, transactionId, TTL);
            return Boolean.TRUE.equals(result);
        }
    }
}
