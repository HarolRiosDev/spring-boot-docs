package dev.springbootdocs.examples.tasks.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springbootdocs.examples.tasks.model.Role;
import dev.springbootdocs.examples.tasks.model.Task;
import dev.springbootdocs.examples.tasks.model.User;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/**
 * Test unitario puro: ejercita el MISMO serializador que {@link CacheConfig} instala para
 * los valores de la caché de Redis, sin necesitar Redis (ni contexto de Spring) para nada.
 *
 * <p>Existe porque el perfil de test usa {@code spring.cache.type: simple}, que guarda el
 * objeto en memoria tal cual y nunca lo serializa — así que ningún test de integración
 * puede detectar un fallo de serialización. Este sí.
 */
class CacheValueSerializationTest {

    private static GenericJacksonJsonRedisSerializer serializer() {
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("dev.springbootdocs.examples.tasks.")
                .build();
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(typeValidator)
                .build();
    }

    private static void setId(Object entity, Long id) throws Exception {
        Field field = entity.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }

    private static Task sampleTask() throws Exception {
        User user = new User("ana", "$2a$10$abcdefghijklmnopqrstuvABCDEFGHIJKLMNOPQRSTUVWXYZ012345", Role.USER);
        setId(user, 7L);
        Task task = new Task("Comprar pan", "En la panaderia de la esquina", false, user);
        setId(task, 1L);
        return task;
    }

    @Test
    void cachedTaskRoundTripsWithoutLeakingThePassword() throws Exception {
        Task task = sampleTask();

        byte[] bytes = serializer().serialize(task);
        String json = new String(bytes, StandardCharsets.UTF_8);
        System.out.println("=== cached JSON ===");
        System.out.println(json);

        assertThat(json).doesNotContain("password");
        assertThat(json).contains("\"@class\":\"dev.springbootdocs.examples.tasks.model.Task\"");

        Object restored = serializer().deserialize(bytes);
        System.out.println("=== restored type === " + restored.getClass().getName());
        assertThat(restored).isInstanceOf(Task.class);

        Task restoredTask = (Task) restored;
        System.out.println("=== restored === id=" + restoredTask.getId()
                + " titulo=" + restoredTask.getTitulo()
                + " completada=" + restoredTask.isCompletada()
                + " user=" + (restoredTask.getUser() == null ? "null" : restoredTask.getUser().getUsername()));
        assertThat(restoredTask.getId()).isEqualTo(1L);
        assertThat(restoredTask.getTitulo()).isEqualTo("Comprar pan");
        assertThat(restoredTask.getUser()).isNotNull();
        assertThat(restoredTask.getUser().getId()).isEqualTo(7L);
        assertThat(restoredTask.getUser().getUsername()).isEqualTo("ana");
    }
}
