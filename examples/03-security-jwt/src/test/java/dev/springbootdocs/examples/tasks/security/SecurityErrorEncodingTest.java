package dev.springbootdocs.examples.tasks.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springbootdocs.examples.tasks.dto.AuthResponse;
import dev.springbootdocs.examples.tasks.dto.LoginRequest;
import dev.springbootdocs.examples.tasks.dto.RegisterRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

/**
 * Con un servidor real (no MockMvc): los 401 y 403 no pasan por Spring MVC, los escriben
 * CustomAuthenticationEntryPoint y CustomAccessDeniedHandler directamente en la respuesta.
 * Si no fijan la codificación, Tomcat usa ISO-8859-1 y las tildes llegan rotas a cualquier
 * cliente que lea el JSON como UTF-8. MockMvc no lo detecta porque su respuesta simulada
 * ya usa UTF-8.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityErrorEncodingTest {

    @LocalServerPort
    private int port;

    @Test
    void unauthorized_isWrittenInUtf8() {
        RestClient client = RestClient.create("http://localhost:" + port);

        String body = client.get().uri("/tasks")
                .exchange((request, response) -> readAsUtf8(response.getBody().readAllBytes()));

        assertThat(body).contains("No autenticado o token inválido");
    }

    @Test
    void forbidden_isWrittenInUtf8() {
        RestClient client = RestClient.create("http://localhost:" + port);
        client.post().uri("/auth/register").body(new RegisterRequest("zack", "password123"))
                .retrieve().toBodilessEntity();
        String token = client.post().uri("/auth/login").body(new LoginRequest("zack", "password123"))
                .retrieve().body(AuthResponse.class).token();

        String body = client.get().uri("/admin/users").header("Authorization", "Bearer " + token)
                .exchange((request, response) -> readAsUtf8(response.getBody().readAllBytes()));

        assertThat(body).contains("No tienes permiso para realizar esta acción");
    }

    private static String readAsUtf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
