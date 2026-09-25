package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Levanta la app con servidores reales: la API en un puerto aleatorio y Actuator en OTRO
 * puerto aleatorio (management.server.port=0), como en producción con el 8081.
 *
 * <p>{@code @AutoConfigureMetrics} es necesaria: en los tests Spring Boot desactiva la
 * exportación de métricas y, sin ella, el endpoint /actuator/prometheus no existe.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
@AutoConfigureMetrics
class ActuatorEndpointsTest {

    @LocalServerPort
    private int apiPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private ObjectMapper objectMapper;

    private final RestClient http = RestClient.create();

    private record Result(int status, String contentType, String body) {
    }

    private Result get(int port, String path, String token) {
        return http.get()
                .uri("http://localhost:" + port + path)
                .headers(headers -> {
                    if (token != null) {
                        headers.setBearerAuth(token);
                    }
                })
                .exchange((request, response) -> new Result(
                        response.getStatusCode().value(),
                        response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                        new String(response.getBody().readAllBytes())));
    }

    private String login(String username, String password) {
        String body = http.post()
                .uri("http://localhost:" + apiPort + "/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(body).get("token").asText();
    }

    private String registerAndLogin(String username) {
        http.post()
                .uri("http://localhost:" + apiPort + "/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"username\":\"" + username + "\",\"password\":\"password123\"}")
                .retrieve()
                .toBodilessEntity();
        return login(username, "password123");
    }

    @Test
    void health_onManagementPort_isPublicAndHidesDetails() {
        Result result = get(managementPort, "/actuator/health", null);

        assertThat(result.status()).isEqualTo(200);
        JsonNode health = objectMapper.readTree(result.body());
        assertThat(health.get("status").asText()).isEqualTo("UP");
        assertThat(health.has("components")).isFalse();
    }

    @Test
    void livenessAndReadiness_arePublicAndUp() {
        Result liveness = get(managementPort, "/actuator/health/liveness", null);
        Result readiness = get(managementPort, "/actuator/health/readiness", null);

        assertThat(liveness.status()).isEqualTo(200);
        assertThat(objectMapper.readTree(liveness.body()).get("status").asText()).isEqualTo("UP");
        assertThat(readiness.status()).isEqualTo(200);
        assertThat(objectMapper.readTree(readiness.body()).get("status").asText()).isEqualTo("UP");
    }

    @Test
    void readiness_includesDatabase_butLivenessDoesNot() {
        String adminToken = login("admin", "admin12345");

        JsonNode readiness = objectMapper.readTree(get(managementPort, "/actuator/health/readiness", adminToken).body());
        JsonNode liveness = objectMapper.readTree(get(managementPort, "/actuator/health/liveness", adminToken).body());

        assertThat(readiness.at("/components/db/status").asText()).isEqualTo("UP");
        assertThat(liveness.at("/components/db").isMissingNode()).isTrue();
    }

    @Test
    void health_asAdmin_showsDatabaseComponent() {
        String adminToken = login("admin", "admin12345");

        Result result = get(managementPort, "/actuator/health", adminToken);

        JsonNode health = objectMapper.readTree(result.body());
        assertThat(health.at("/components/db/status").asText()).isEqualTo("UP");
    }

    @Test
    void metrics_isOnlyForAdmin() {
        String userToken = registerAndLogin("actuator-user");
        String adminToken = login("admin", "admin12345");

        assertThat(get(managementPort, "/actuator/metrics", null).status()).isEqualTo(401);
        assertThat(get(managementPort, "/actuator/metrics", userToken).status()).isEqualTo(403);
        assertThat(get(managementPort, "/actuator/metrics", adminToken).status()).isEqualTo(200);
    }

    @Test
    void prometheus_isPublicAndUsesPrometheusFormat() {
        Result result = get(managementPort, "/actuator/prometheus", null);

        assertThat(result.status()).isEqualTo(200);
        assertThat(result.contentType()).startsWith("text/plain");
        assertThat(result.body()).contains("jvm_memory_used_bytes");
    }

    @Test
    void info_showsVersionFromBuildInfo() {
        Result result = get(managementPort, "/actuator/info", null);

        assertThat(result.status()).isEqualTo(200);
        assertThat(objectMapper.readTree(result.body()).at("/build/artifact").asText())
                .isEqualTo("tasks-observability");
    }

    @Test
    void actuator_isNotServedOnApiPort() {
        // en el puerto de la API no hay Actuator: la ruta cae en "anyRequest().authenticated()"
        assertThat(get(apiPort, "/actuator/health", null).status()).isEqualTo(401);
    }
}
