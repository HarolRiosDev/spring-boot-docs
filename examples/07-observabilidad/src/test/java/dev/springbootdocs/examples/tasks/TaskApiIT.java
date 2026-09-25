package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.redis.testcontainers.RedisContainer;
import dev.springbootdocs.examples.tasks.dto.LoginRequest;
import dev.springbootdocs.examples.tasks.dto.RegisterRequest;
import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
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
// que el resto de tests de Surefire NO intenten hablar con un Redis real que no tienen
// corriendo). Sin este override, @Cacheable escribiría en el ConcurrentMapCacheManager en
// memoria de Spring y jamás tocaría el contenedor Redis real levantado por @ServiceConnection
// más abajo — el test pasaría igual hasta la última aserción, que fallaría siempre.
@Testcontainers
@SpringBootTest(properties = "spring.cache.type=redis")
@AutoConfigureMockMvc
@AutoConfigureMetrics
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

    @Test
    void cacheStatistics_reachPrometheus_throughRealRedis() throws Exception {
        String username = "jana";
        String password = "password123";
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(username, password))))
                .andExpect(status().isCreated());
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andReturn();
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("token").asText();
        MvcResult createResult = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("Contar aciertos", "desc", false))))
                .andReturn();
        long taskId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // primera lectura: fallo de caché (va a Postgres y guarda en Redis); segunda: acierto
        mockMvc.perform(get("/tasks/{id}", taskId).header("Authorization", "Bearer " + token));
        mockMvc.perform(get("/tasks/{id}", taskId).header("Authorization", "Bearer " + token));

        // RedisCacheManager solo cuenta aciertos con spring.cache.redis.enable-statistics=true;
        // sin esa propiedad la métrica existiría, pero siempre a 0, y el panel de Grafana
        // quedaría plano sin ningún error
        String prometheus = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher hits = Pattern.compile("(?m)^cache_gets_total\\{[^}]*result=\"hit\"[^}]*} (\\S+)$").matcher(prometheus);
        assertThat(hits.find()).isTrue();
        assertThat(Double.parseDouble(hits.group(1))).isGreaterThanOrEqualTo(1.0);
    }
}
