package dev.springbootdocs.examples.tasks.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClient;

/**
 * Con un servidor real (no MockMvc): cuando una petición falla con 404 o 500, Tomcat la
 * reenvía a la página de error de Spring Boot (/error), y esa segunda pasada también cruza
 * Spring Security. MockMvc no hace ese reenvío, por eso este test necesita RANDOM_PORT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorPageSecurityTest {

    @LocalServerPort
    private int port;

    @Test
    void unknownPublicPath_withoutToken_returns404_not401() {
        int status = RestClient.create().get()
                .uri("http://localhost:" + port + "/auth/no-existe")
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(404);
    }
}
