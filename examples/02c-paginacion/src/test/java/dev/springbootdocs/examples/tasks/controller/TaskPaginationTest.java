package dev.springbootdocs.examples.tasks.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Los tests de TaskControllerTest crean tareas que se quedan en la base de datos, así que
// aquí no se comprueban totales exactos de las 60 tareas de partida, sino propiedades
// que se cumplen haya las tareas que haya: "todas completadas", "orden descendente"...
@SpringBootTest
@AutoConfigureMockMvc
class TaskPaginationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode getJson(MockHttpServletRequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private static List<JsonNode> toList(JsonNode array) {
        List<JsonNode> list = new ArrayList<>();
        array.forEach(list::add);
        return list;
    }

    // Más reciente primero y, entre tareas con la misma fecha, el id mayor primero
    private static void assertNewestFirst(List<JsonNode> tasks) {
        for (int i = 1; i < tasks.size(); i++) {
            JsonNode previous = tasks.get(i - 1);
            JsonNode current = tasks.get(i);
            Instant previousDate = Instant.parse(previous.get("fechaCreacion").asString());
            Instant currentDate = Instant.parse(current.get("fechaCreacion").asString());
            assertThat(currentDate).isBeforeOrEqualTo(previousDate);
            if (currentDate.equals(previousDate)) {
                assertThat(current.get("id").asLong()).isLessThan(previous.get("id").asLong());
            }
        }
    }

    @Test
    void listTasks_byDefault_returnsFirstPageOf20NewestFirst() throws Exception {
        JsonNode body = getJson(get("/tasks"));

        assertThat(body.get("page").get("size").asInt()).isEqualTo(20);
        assertThat(body.get("page").get("number").asInt()).isZero();
        assertThat(body.get("page").get("totalElements").asLong()).isGreaterThanOrEqualTo(60);
        assertThat(toList(body.get("content"))).hasSize(20);
        assertNewestFirst(toList(body.get("content")));
    }

    @Test
    void walkingAllPages_returnsEveryTaskOnceInAStableOrder() throws Exception {
        JsonNode first = getJson(get("/tasks").param("size", "7"));
        int totalPages = first.get("page").get("totalPages").asInt();
        long totalElements = first.get("page").get("totalElements").asLong();

        List<JsonNode> tasks = new ArrayList<>();
        for (int page = 0; page < totalPages; page++) {
            JsonNode body = getJson(get("/tasks").param("size", "7").param("page", String.valueOf(page)));
            tasks.addAll(toList(body.get("content")));
        }

        List<Long> ids = tasks.stream().map(task -> task.get("id").asLong()).toList();
        assertThat(ids).hasSize((int) totalElements).doesNotHaveDuplicates();
        assertNewestFirst(tasks);
    }

    @Test
    void listTasks_withClientSort_keepsTheTieBreaker() throws Exception {
        JsonNode body = getJson(get("/tasks").param("sort", "fechaCreacion,desc").param("size", "100"));

        assertNewestFirst(toList(body.get("content")));
    }

    @Test
    void listTasks_sortedById_returnsAscendingIds() throws Exception {
        JsonNode body = getJson(get("/tasks").param("sort", "id"));

        List<Long> ids = toList(body.get("content")).stream().map(task -> task.get("id").asLong()).toList();
        assertThat(ids).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void listTasks_withHugeSize_isCappedAt100() throws Exception {
        mockMvc.perform(get("/tasks").param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void listTasks_withZeroSize_usesTheDefaultSize() throws Exception {
        mockMvc.perform(get("/tasks").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    void listTasks_withNegativePage_returnsTheFirstPage() throws Exception {
        mockMvc.perform(get("/tasks").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    void listTasks_pastTheLastPage_returnsEmptyContentWithTotals() throws Exception {
        JsonNode body = getJson(get("/tasks").param("page", "999"));

        assertThat(toList(body.get("content"))).isEmpty();
        assertThat(body.get("page").get("number").asInt()).isEqualTo(999);
        assertThat(body.get("page").get("totalElements").asLong()).isGreaterThanOrEqualTo(60);
    }

    // page * size no cabe en un int: sin protección, Spring Data lanzaría una excepción (un 500)
    @Test
    void listTasks_withAPageBeyondIntegerOffsets_returnsEmptyContentWithTotals() throws Exception {
        JsonNode body = getJson(get("/tasks").param("page", "99999999").param("size", "100"));

        assertThat(toList(body.get("content"))).isEmpty();
        assertThat(body.get("page").get("number").asInt()).isEqualTo(99999999);
        assertThat(body.get("page").get("totalElements").asLong()).isGreaterThanOrEqualTo(60);
    }

    @Test
    void listTasks_sortedByUnknownField_returnsApiError() throws Exception {
        mockMvc.perform(get("/tasks").param("sort", "noexiste"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "No se puede ordenar por 'noexiste'. Campos permitidos: fechaCreacion, titulo, completada, id"));
    }

    @Test
    void listTasks_sortedByFieldOutsideTheList_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/tasks").param("sort", "descripcion"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void listTasks_withMisspelledDirection_returnsBadRequestNamingIt() throws Exception {
        mockMvc.perform(get("/tasks").param("sort", "titulo,sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "No se puede ordenar por 'sideways'. Campos permitidos: fechaCreacion, titulo, completada, id"));
    }

    @Test
    void listTasks_withNonBooleanCompletada_returnsApiError() throws Exception {
        mockMvc.perform(get("/tasks").param("completada", "quizas"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("El parámetro 'completada' tiene un valor no válido: 'quizas'"));
    }

    @Test
    void listTasks_filteredByCompletada_returnsOnlyCompletedTasks() throws Exception {
        mockMvc.perform(get("/tasks").param("completada", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.completada == false)]").isEmpty());
    }

    @Test
    void listTasks_withEmptyCompletada_doesNotFilter() throws Exception {
        long sinFiltro = getJson(get("/tasks")).get("page").get("totalElements").asLong();

        JsonNode body = getJson(get("/tasks").param("completada", ""));

        assertThat(body.get("page").get("totalElements").asLong()).isEqualTo(sinFiltro);
    }

    @Test
    void listTasks_searchingInTitle_ignoresCase() throws Exception {
        JsonNode body = getJson(get("/tasks").param("q", "INFORME"));

        List<JsonNode> tasks = toList(body.get("content"));
        assertThat(tasks).isNotEmpty();
        assertThat(tasks).allSatisfy(task ->
                assertThat(task.get("titulo").asString().toLowerCase(Locale.ROOT)).contains("informe"));
    }

    @Test
    void listTasks_searchingWithSurroundingSpaces_trimsTheText() throws Exception {
        long exacto = getJson(get("/tasks").param("q", "informe")).get("page").get("totalElements").asLong();

        JsonNode body = getJson(get("/tasks").param("q", "  informe  "));

        assertThat(body.get("page").get("totalElements").asLong()).isEqualTo(exacto);
    }

    @Test
    void listTasks_searchingOnlySpaces_doesNotFilter() throws Exception {
        long sinFiltro = getJson(get("/tasks")).get("page").get("totalElements").asLong();

        JsonNode body = getJson(get("/tasks").param("q", "   "));

        assertThat(body.get("page").get("totalElements").asLong()).isEqualTo(sinFiltro);
    }

    @Test
    void listTasks_searchingPercent_matchesItLiterally() throws Exception {
        JsonNode body = getJson(get("/tasks").param("q", "%"));

        List<JsonNode> tasks = toList(body.get("content"));
        assertThat(tasks).isNotEmpty();
        assertThat(tasks).allSatisfy(task -> assertThat(task.get("titulo").asString()).contains("%"));
    }

    @Test
    void listTasks_searchingUnderscore_matchesItLiterally() throws Exception {
        JsonNode body = getJson(get("/tasks").param("q", "_"));

        List<JsonNode> tasks = toList(body.get("content"));
        assertThat(tasks).isNotEmpty();
        assertThat(tasks).allSatisfy(task -> assertThat(task.get("titulo").asString()).contains("_"));
    }

    @Test
    void listTasks_combiningFilters_appliesBoth() throws Exception {
        JsonNode body = getJson(get("/tasks").param("completada", "false").param("q", "informe"));

        List<JsonNode> tasks = toList(body.get("content"));
        assertThat(tasks).isNotEmpty();
        assertThat(tasks).allSatisfy(task -> {
            assertThat(task.get("completada").asBoolean()).isFalse();
            assertThat(task.get("titulo").asString().toLowerCase(Locale.ROOT)).contains("informe");
        });
    }

    @Test
    void recientes_firstSlice_hasNextAndNoTotals() throws Exception {
        JsonNode body = getJson(get("/tasks/recientes"));

        assertThat(toList(body.get("content"))).hasSize(20);
        assertThat(body.get("number").asInt()).isZero();
        assertThat(body.get("size").asInt()).isEqualTo(20);
        assertThat(body.get("hasNext").asBoolean()).isTrue();
        assertThat(body.has("totalElements")).isFalse();
        assertThat(body.has("page")).isFalse();
        assertNewestFirst(toList(body.get("content")));
    }

    @Test
    void recientes_withRoomForEverything_hasNoNext() throws Exception {
        JsonNode body = getJson(get("/tasks/recientes").param("size", "100"));

        assertThat(body.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void recientes_pastTheEnd_isEmptyWithoutNext() throws Exception {
        JsonNode body = getJson(get("/tasks/recientes").param("page", "999"));

        assertThat(toList(body.get("content"))).isEmpty();
        assertThat(body.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void recientes_withAPageBeyondIntegerOffsets_isEmptyWithoutNext() throws Exception {
        JsonNode body = getJson(get("/tasks/recientes").param("page", "99999999").param("size", "100"));

        assertThat(toList(body.get("content"))).isEmpty();
        assertThat(body.get("hasNext").asBoolean()).isFalse();
    }

    @Test
    void recientes_ignoresTheClientSort() throws Exception {
        JsonNode body = getJson(get("/tasks/recientes").param("sort", "id,asc"));

        assertNewestFirst(toList(body.get("content")));
    }
}
