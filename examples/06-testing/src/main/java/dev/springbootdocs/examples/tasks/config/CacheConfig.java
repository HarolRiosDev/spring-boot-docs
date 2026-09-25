package dev.springbootdocs.examples.tasks.config;

import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final String ENTITY_BASE_PACKAGE = "dev.springbootdocs.examples.tasks.";

    // Spring Boot usa este bean como configuración por defecto de cada caché de Redis que
    // crea: las entradas caducan a los 10 minutos y los valores se guardan en JSON.
    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(ENTITY_BASE_PACKAGE)
                .build();
        GenericJacksonJsonRedisSerializer valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
    }
}
