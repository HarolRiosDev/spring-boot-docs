# Spring Boot desde cero

Sitio de documentación para aprender Spring Boot desde cero, en español, con un proyecto ejecutable por tema.

📖 **Léelo aquí: [harolriosdev.github.io/spring-boot-docs](https://harolriosdev.github.io/spring-boot-docs/)**

- Documentación: [`docs-site/`](docs-site/), un sitio [Docusaurus](https://docusaurus.io/) que se publica en GitHub Pages con GitHub Actions.
- Ejemplos ejecutables: [`examples/`](examples/), un proyecto Maven independiente por tema.

## Ejemplos

| Carpeta | Tema | Necesita |
|---|---|---|
| [`00-hello-world`](examples/00-hello-world/) | La aplicación Spring Boot más pequeña posible | JDK 21 |
| [`01-fundamentos`](examples/01-fundamentos/) | API REST de tareas: capas, validación, errores | JDK 21 |
| [`02-persistencia`](examples/02-persistencia/) | Spring Data JPA, PostgreSQL, Flyway | JDK 21 (Docker opcional) |
| [`03-security-jwt`](examples/03-security-jwt/) | Spring Security, JWT, roles | JDK 21 (Docker opcional) |
| [`04-cache-redis`](examples/04-cache-redis/) | Spring Cache con Redis | JDK 21 (Docker opcional) |
| [`05-kafka`](examples/05-kafka/) | Productores y consumidores con Kafka | JDK 21 (Docker opcional) |
| [`06-testing`](examples/06-testing/) | JUnit, Mockito, Testcontainers | JDK 21 (Docker para `./mvnw verify`) |
| [`07-observabilidad`](examples/07-observabilidad/) | Actuator, métricas, logs, OpenAPI | JDK 21 (Docker opcional) |

Sin Docker, los ejemplos con base de datos arrancan con el perfil `h2` (`SPRING_PROFILES_ACTIVE=h2 ./mvnw spring-boot:run`); el README de cada uno lo explica.

## Ejecutar un ejemplo

```bash
cd examples/00-hello-world
./mvnw spring-boot:run
```

Requiere JDK 21+ instalado (usa `JAVA_HOME` si tu `java` por defecto es más antiguo).

## Desarrollo local del sitio

```bash
cd docs-site
npm install
npm start
```

## Licencia

[MIT](LICENSE)
