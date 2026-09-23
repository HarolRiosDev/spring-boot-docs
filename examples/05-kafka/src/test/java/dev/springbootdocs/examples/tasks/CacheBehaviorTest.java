package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Verifies caching against the REAL CacheManager the test profile wires up
 * (ConcurrentMapCacheManager, via spring.cache.type: simple) — not a mock of the cache
 * abstraction. This is the same @Cacheable/@CacheEvict machinery that runs against
 * Redis in production; only the backend differs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = "task-events")
class CacheBehaviorTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CacheManager cacheManager;

    private String registerAndLogin(String username) throws Exception {
        String password = "password123";
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(username, password))));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private Long createTask(String token, String titulo) throws Exception {
        String json = objectMapper.writeValueAsString(new TaskRequest(titulo, "desc", false));
        MvcResult result = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private Cache tasksCache() {
        Cache cache = cacheManager.getCache("tasks");
        assertThat(cache).isNotNull();
        return cache;
    }

    @Test
    void getTaskById_populatesCache() throws Exception {
        String token = registerAndLogin("cara");
        Long id = createTask(token, "Tarea de cara");
        assertThat(tasksCache().get(id)).isNull();

        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(tasksCache().get(id)).isNotNull();
    }

    @Test
    void updateTask_evictsCacheEntry() throws Exception {
        String token = registerAndLogin("dan");
        Long id = createTask(token, "Original");
        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token));
        assertThat(tasksCache().get(id)).isNotNull();

        String updateJson = objectMapper.writeValueAsString(new TaskRequest("Actualizada", "desc", true));
        mockMvc.perform(put("/tasks/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk());

        assertThat(tasksCache().get(id)).isNull();
    }

    @Test
    void deleteTask_evictsCacheEntry() throws Exception {
        String token = registerAndLogin("eve");
        Long id = createTask(token, "Para borrar");
        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + token));
        assertThat(tasksCache().get(id)).isNotNull();

        mockMvc.perform(delete("/tasks/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(tasksCache().get(id)).isNull();
    }

    @Test
    void cacheHit_stillEnforcesOwnership_forDifferentUser() throws Exception {
        String ownerToken = registerAndLogin("fay");
        String otherToken = registerAndLogin("gus");
        Long id = createTask(ownerToken, "Tarea de fay");

        // primera lectura: cache miss, la sirve el dueño
        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        assertThat(tasksCache().get(id)).isNotNull();

        // segunda lectura del MISMO id: acierto de caché, pero pedida por alguien sin permiso
        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedUpdate_byNonOwner_doesNotEvictCache() throws Exception {
        String ownerToken = registerAndLogin("hank");
        String otherToken = registerAndLogin("iris");
        Long id = createTask(ownerToken, "Tarea de hank");
        mockMvc.perform(get("/tasks/{id}", id).header("Authorization", "Bearer " + ownerToken));
        assertThat(tasksCache().get(id)).isNotNull();

        String updateJson = objectMapper.writeValueAsString(new TaskRequest("Hackeada", null, false));
        mockMvc.perform(put("/tasks/{id}", id)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isForbidden());

        assertThat(tasksCache().get(id)).isNotNull();
    }
}
