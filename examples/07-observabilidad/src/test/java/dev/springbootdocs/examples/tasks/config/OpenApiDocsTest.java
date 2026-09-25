package dev.springbootdocs.examples.tasks.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode apiDocs() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void apiDocs_isPublic_andDescribesTheEndpoints() throws Exception {
        JsonNode paths = apiDocs().get("paths");

        assertThat(paths.has("/tasks")).isTrue();
        assertThat(paths.has("/tasks/{id}")).isTrue();
        assertThat(paths.has("/auth/login")).isTrue();
        assertThat(paths.has("/admin/users")).isTrue();
    }

    @Test
    void apiDocs_declaresBearerJwtScheme_asGlobalRequirement() throws Exception {
        JsonNode docs = apiDocs();

        JsonNode scheme = docs.at("/components/securitySchemes/" + OpenApiConfig.BEARER_SCHEME);
        assertThat(scheme.get("type").asText()).isEqualTo("http");
        assertThat(scheme.get("scheme").asText()).isEqualTo("bearer");
        assertThat(scheme.get("bearerFormat").asText()).isEqualTo("JWT");
        assertThat(docs.at("/security/0").has(OpenApiConfig.BEARER_SCHEME)).isTrue();
    }

    @Test
    void authEndpoints_overrideTheGlobalRequirement() throws Exception {
        // "security": [] en la operación: Swagger UI no pide token para hacer login
        JsonNode login = apiDocs().at("/paths/~1auth~1login/post");

        assertThat(login.get("security").isArray()).isTrue();
        assertThat(login.get("security")).isEmpty();
    }

    @Test
    void operations_carrySummaryAndTag() throws Exception {
        JsonNode createTask = apiDocs().at("/paths/~1tasks/post");

        assertThat(createTask.get("summary").asText()).isEqualTo("Crear una tarea");
        assertThat(createTask.at("/tags/0").asText()).isEqualTo("Tareas");
    }

    @Test
    void swaggerUi_isServed_outsideProd() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
