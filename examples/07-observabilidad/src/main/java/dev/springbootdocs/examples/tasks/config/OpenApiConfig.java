package dev.springbootdocs.examples.tasks.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI tasksOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tasks API")
                        .version("v1")
                        .description("API de gestión de tareas con autenticación JWT. "
                                + "Obtén un token en POST /auth/login y pégalo en \"Authorize\"."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                // requisito global: todos los endpoints piden el token salvo los que lo anulen
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
