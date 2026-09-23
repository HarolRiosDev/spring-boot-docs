package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

// La propiedad "spring.cache.type=redis" sobreescribe, solo para el ApplicationContext de
// esta clase, el "simple" heredado de src/test/resources/application.yml (necesario para
// que los otros 44 tests de Surefire NO intenten hablar con un Redis real que no tienen
// corriendo). Sin este override, @Cacheable escribiría en el ConcurrentMapCacheManager en
// memoria de Spring y jamás tocaría el contenedor Redis real levantado por @ServiceConnection
// más abajo — el test pasaría igual hasta la última aserción, que fallaría siempre.
@Testcontainers
@SpringBootTest(properties = "spring.cache.type=redis")
@AutoConfigureMockMvc
class TaskApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Container
    @ServiceConnection
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void fullFlow_registerLoginCreateAndReadTask_throughRealPostgresAndRedis() throws Exception {
        String username = "itzel";
        String password = "password123";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, password))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();

        MvcResult createResult = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskRequest("Aprender Testcontainers", "desc", false))))
                .andExpect(status().isCreated())
                .andReturn();
        long taskId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(get("/tasks/{id}", taskId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // La lectura anterior pasó por CachedTaskLookup (@Cacheable) contra el Redis real
        // del contenedor: confirmarlo leyendo la clave directamente, con el mismo cliente
        // redis-cli que se usa para verificación manual en Fase 4. Se compara la lista de
        // líneas exactas (no un "contains" sobre el texto crudo) para no confundir
        // "tasks::1" con un futuro "tasks::10".
        String cacheKey = "tasks::" + taskId;
        org.testcontainers.containers.Container.ExecResult keysResult =
                redis.execInContainer("redis-cli", "keys", "tasks::*");
        assertThat(keysResult.getStdout().lines()).contains(cacheKey);

        // Invalidación: un PUT (update) dispara @CacheEvict(value = "tasks", key = "#id")
        // en TaskServiceImpl.update. Si esa anotación se quitara, la clave seguiría presente
        // y esta aserción fallaría — es la única verificación de todo el proyecto que prueba
        // @CacheEvict contra un Redis real en vez de contra el ConcurrentMapCacheManager simulado.
        mockMvc.perform(put("/tasks/{id}", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TaskRequest("Aprender Testcontainers (editado)", "desc", true))))
                .andExpect(status().isOk());

        org.testcontainers.containers.Container.ExecResult keysAfterUpdateResult =
                redis.execInContainer("redis-cli", "keys", "tasks::*");
        assertThat(keysAfterUpdateResult.getStdout().lines()).doesNotContain(cacheKey);
    }
}
