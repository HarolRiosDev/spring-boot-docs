package dev.springbootdocs.examples.tasks.security;

import dev.springbootdocs.examples.tasks.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        ApiError error = ApiError.of(HttpStatus.FORBIDDEN.value(), "No tienes permiso para realizar esta acción");
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Sin esto, Tomcat escribe en ISO-8859-1 y las tildes llegan rotas a un cliente que lee UTF-8
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        objectMapper.writeValue(response.getWriter(), error);
    }
}
