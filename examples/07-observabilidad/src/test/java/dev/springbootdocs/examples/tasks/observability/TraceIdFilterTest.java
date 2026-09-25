package dev.springbootdocs.examples.tasks.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springbootdocs.examples.tasks.dto.LoginRequest;
import dev.springbootdocs.examples.tasks.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code @AutoConfigureTracing}: en los tests Spring Boot desactiva la exportación Y la
 * propagación de trazas. Los ids se siguen generando (por eso el resto de tests no la
 * necesita), pero sin ella se ignoraría la cabecera "traceparent" que envía un cliente.
 * Esta aplicación no exporta spans a ningún sitio (no hay Zipkin en el classpath).
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
class TraceIdFilterTest {

    // Spring Boot configura Brave con ids de traza de 128 bits: 32 caracteres hexadecimales
    private static final String TRACE_ID_PATTERN = "[0-9a-f]{32}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndLogin(String username) throws Exception {
        String password = "password123";
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegisterRequest(username, password))));

        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void response_carriesTraceIdHeader() throws Exception {
        String token = registerAndLogin("trace-carla");

        MvcResult result = mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER)).matches(TRACE_ID_PATTERN);
    }

    @Test
    void differentRequests_haveDifferentTraceIds() throws Exception {
        String token = registerAndLogin("trace-dario");

        String first = mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);
        String second = mockMvc.perform(get("/tasks").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER);

        assertThat(first).matches(TRACE_ID_PATTERN);
        assertThat(second).matches(TRACE_ID_PATTERN);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void unauthorizedResponse_alsoCarriesTraceId() throws Exception {
        // justo cuando más se necesita: una petición rechazada por Spring Security
        MvcResult result = mockMvc.perform(get("/tasks"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER)).matches(TRACE_ID_PATTERN);
    }

    @Test
    void incomingTraceparent_isContinued_notReplaced() throws Exception {
        // un gateway u otro servicio que ya abrió la traza la manda en la cabecera W3C
        // "traceparent": la respuesta devuelve ESE traceId, no uno nuevo
        String incomingTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";

        MvcResult result = mockMvc.perform(get("/tasks")
                        .header("traceparent", "00-" + incomingTraceId + "-00f067aa0ba902b7-01"))
                .andReturn();

        assertThat(result.getResponse().getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(incomingTraceId);
    }
}
