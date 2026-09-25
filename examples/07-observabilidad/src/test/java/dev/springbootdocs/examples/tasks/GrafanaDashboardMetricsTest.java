package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springbootdocs.examples.tasks.dto.LoginRequest;
import dev.springbootdocs.examples.tasks.dto.RegisterRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Ni este proyecto ni el CI levantan Grafana. Este test hace la comprobación que más fallaría
 * en la práctica: que cada métrica que consulta el dashboard existe de verdad en
 * /actuator/prometheus. Un nombre mal escrito dejaría un panel vacío sin ningún error.
 *
 * <p>Usa la caché de Redis de producción (spring.cache.type=redis) porque
 * ConcurrentMapCacheManager no publica métricas. No hace falta un Redis corriendo:
 * RedisCacheManager no conecta hasta la primera lectura, y este test no lee de la caché.
 */
@SpringBootTest(properties = "spring.cache.type=redis")
@AutoConfigureMockMvc
@AutoConfigureMetrics
class GrafanaDashboardMetricsTest {

    // Maven ejecuta los tests con la carpeta del proyecto como directorio de trabajo
    private static final Path DASHBOARD = Path.of("observability", "grafana", "dashboards", "tasks-api.json");

    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_:][a-zA-Z0-9_:]*");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Extrae los nombres de métrica de una consulta PromQL: quita selectores de etiquetas
     * ({...}), rangos ([1m]) y agrupaciones (by (...)); lo que queda y no va seguido de
     * "(" (una función como rate o sum) es un nombre de métrica.
     */
    static Set<String> metricNames(String expr) {
        String cleaned = expr
                .replaceAll("\\{[^}]*}", " ")
                .replaceAll("\\[[^]]*]", " ")
                .replaceAll("\\b(by|without)\\s*\\([^)]*\\)", " ");
        Set<String> names = new TreeSet<>();
        Matcher matcher = IDENTIFIER.matcher(cleaned);
        while (matcher.find()) {
            boolean isFunction = cleaned.substring(matcher.end()).stripLeading().startsWith("(");
            if (!isFunction) {
                names.add(matcher.group());
            }
        }
        return names;
    }

    private List<String> dashboardExpressions() throws Exception {
        assertThat(DASHBOARD).exists();
        JsonNode dashboard = objectMapper.readTree(Files.readString(DASHBOARD));
        List<String> expressions = new ArrayList<>();
        for (JsonNode panel : dashboard.get("panels")) {
            for (JsonNode target : panel.get("targets")) {
                expressions.add(target.get("expr").asText());
            }
        }
        return expressions;
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(username, "password123"))));
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "password123"))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void metricNames_ignoresFunctionsLabelsAndRanges() {
        assertThat(metricNames(
                "histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{uri!~\"/actuator.*\"}[5m])))"))
                .containsExactly("http_server_requests_seconds_bucket");
    }

    @Test
    void everyMetricUsedByTheDashboard_isExposedByTheApp() throws Exception {
        // las métricas http_server_requests_* no existen hasta la primera petición
        String token = registerAndLogin("grafana-fede");
        mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + token)).andExpect(status().isOk());

        String prometheus = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String expr : dashboardExpressions()) {
            Set<String> names = metricNames(expr);
            assertThat(names).as("métricas en «%s»", expr).isNotEmpty();
            for (String name : names) {
                assertThat(prometheus)
                        .as("la métrica %s del dashboard no aparece en /actuator/prometheus", name)
                        .containsPattern("(?m)^" + Pattern.quote(name) + "[{ ]");
            }
        }
    }
}
