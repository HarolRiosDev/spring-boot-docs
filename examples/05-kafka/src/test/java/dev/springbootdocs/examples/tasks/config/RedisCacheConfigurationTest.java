package dev.springbootdocs.examples.tasks.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.springbootdocs.examples.tasks.model.Role;
import dev.springbootdocs.examples.tasks.model.Task;
import dev.springbootdocs.examples.tasks.model.User;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Comprueba que la caché {@code tasks} de Redis recibe la configuración de {@link CacheConfig}:
 * TTL de 10 minutos y serializador JSON. Sin ella, Spring Boot usaría su configuración por
 * defecto: sin caducidad y con el serializador de Java, que no sabe guardar un {@link Task}.
 *
 * <p>Activa la caché de Redis de producción ({@code spring.cache.type=redis}) sin tener Redis:
 * el {@code RedisCacheManager} no se conecta hasta la primera lectura o escritura, y este test
 * solo inspecciona su configuración.
 */
@SpringBootTest(properties = "spring.cache.type=redis")
@EmbeddedKafka(partitions = 1, topics = "task-events")
class RedisCacheConfigurationTest {

    @Autowired
    private CacheManager cacheManager;

    @Test
    void tasksCache_expiresAfterTenMinutes() {
        RedisCacheConfiguration configuration = tasksCacheConfiguration();

        assertThat(configuration.getTtlFunction().getTimeToLive("1", sampleTask()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void tasksCache_canSerializeATask() {
        RedisCacheConfiguration configuration = tasksCacheConfiguration();

        assertThatCode(() -> configuration.getValueSerializationPair().write(sampleTask()))
                .doesNotThrowAnyException();
    }

    private RedisCacheConfiguration tasksCacheConfiguration() {
        RedisCache cache = (RedisCache) cacheManager.getCache("tasks");
        return cache.getCacheConfiguration();
    }

    private static Task sampleTask() {
        User owner = new User("owner", "hash", Role.USER);
        ReflectionTestUtils.setField(owner, "id", 1L);
        Task task = new Task("Comprar pan", "desc", false, owner);
        ReflectionTestUtils.setField(task, "id", 10L);
        return task;
    }
}
