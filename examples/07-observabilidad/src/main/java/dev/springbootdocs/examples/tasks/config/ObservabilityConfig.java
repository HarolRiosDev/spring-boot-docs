package dev.springbootdocs.examples.tasks.config;

import dev.springbootdocs.examples.tasks.observability.TraceIdFilter;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ObservabilityConfig {

    /**
     * El orden importa: el filtro de observación HTTP de Spring Boot (que crea el span)
     * corre con HIGHEST_PRECEDENCE + 1, y la cadena de Spring Security con -100. Con
     * HIGHEST_PRECEDENCE + 10 el span ya existe y la seguridad todavía no ha rechazado nada.
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter(Tracer tracer) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter(tracer));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
