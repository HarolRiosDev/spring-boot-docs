# Fase 0 — Fundación del repo (diseño)

> Estado: **diseño aprobado por el usuario el 2026-09-14**. Licencia confirmada: MIT. Siguiente paso: generar el plan de implementación con `writing-plans`. No se ha implementado nada todavía (la carpeta no es ni siquiera un repo git).

## Contexto y objetivo general del proyecto

El usuario quiere un **sitio de documentación para aprender Spring Boot** (no una app Spring Boot en producción). Debe cubrir Spring Boot básico, Spring Security/Auth, caché/Redis, Kafka, BBDD, testing, etc., de forma didáctica, y desplegarse automáticamente vía GitHub Actions.

Decisiones de alcance ya tomadas (ver preguntas/respuestas de la sesión):
- **No** es una app Spring Boot corriendo en un hosting — es documentación. Esto simplifica el despliegue: sitio estático a GitHub Pages.
- Generador de sitio: **Docusaurus**.
- Además de la doc en Markdown, habrá **proyectos Spring Boot ejecutables** de ejemplo (uno por tema), en un monorepo.
- Nivel del lector objetivo: **sabe Java, es nuevo en Spring Boot**.
- Idioma del contenido: **español** (código/términos técnicos en inglés, como es estándar).
- Se añade una sección **opcional de repaso de Java** (POO, herencia, polimorfismo, interfaces, colecciones, lambdas/streams) para quien quiera repasar antes de entrar a Spring Boot — sin proyecto ejecutable propio, solo снippets Java puro.
- El usuario pidió explícitamente que el sitio sea **visualmente agradable**, no solo funcional.

## Roadmap completo (decomposición en fases — cada una es su propio ciclo diseño→plan→implementación)

| Fase | Tema | Ejemplo runnable |
|---|---|---|
| **0** | Fundación del repo: Docusaurus + esqueleto del índice completo + 1er ejemplo + pipeline CI/CD funcionando de punta a punta | `00-hello-world` |
| **R** *(opcional, standalone, sin infra)* | Repaso de Java: POO, herencia, polimorfismo, interfaces, colecciones, lambdas/streams | — (solo snippets) |
| 1 | Fundamentos Spring Boot: DI, beans, REST, capas, validación, manejo de errores | `01-fundamentos` |
| 2 | Persistencia: Spring Data JPA, Postgres, Flyway, docker-compose | `02-persistencia` |
| 3 | Seguridad y Auth: Spring Security, JWT, roles | `03-security-jwt` |
| 4 | Caché y Redis: Spring Cache + Redis | `04-cache-redis` |
| 5 | Mensajería: Spring Kafka (producer/consumer) | `05-kafka` |
| 6 | Testing: JUnit, Mockito, Testcontainers | `06-testing` |
| 7 | Producción: Actuator, métricas, logging, Docker | `07-observabilidad` |

Solo la **Fase 0** está diseñada en detalle por ahora. Las fases 1-7 y R se brainstormean/especifican cuando llegue su turno.

## Decisiones técnicas ya cerradas

- **Build tool de los ejemplos**: Maven (elegido por ser más explícito/legible para quien aprende).
- **Versión de Java**: 21 LTS.
- **Estructura de `/examples`**: proyectos **independientes y autocontenidos** por tema (Opción A de 3 propuestas — descartadas: reactor multi-módulo Maven, y "una sola app que evoluciona por tags de git"). Motivo: un lector debe poder clonar/copiar solo la carpeta de una lección y que funcione sola.

## Diseño detallado de la Fase 0

### 1. Estructura del repo

```
/
├── docs-site/                    # proyecto Docusaurus (nombre distinto a su subcarpeta interna /docs para evitar confusión)
│   ├── docs/
│   │   ├── 00-bienvenida/
│   │   ├── repaso-java/          # opcional, standalone
│   │   ├── 01-fundamentos/
│   │   ├── 02-persistencia/
│   │   ├── 03-security-jwt/
│   │   ├── 04-cache-redis/
│   │   ├── 05-kafka/
│   │   ├── 06-testing/
│   │   └── 07-observabilidad/
│   ├── src/                      # homepage custom, componentes React, CSS
│   ├── static/
│   ├── docusaurus.config.js
│   └── sidebars.js
├── examples/
│   └── 00-hello-world/           # proyecto Maven Spring Boot autocontenido
├── .github/workflows/
│   ├── deploy-docs.yml           # build Docusaurus → GitHub Pages
│   └── examples-ci.yml           # build+test de cada proyecto en /examples
└── README.md
```

### 2. Sitio Docusaurus — estructura y diseño visual

Sidebar en orden: Bienvenida → Repaso de Java (opcional) → Fundamentos → Persistencia → Seguridad/Auth → Caché/Redis → Kafka → Testing → Producción. Cada categoría de fases 1-7 lleva ya en esta fase una página placeholder ("en construcción") para que el índice completo sea visible desde el día uno.

Requisitos visuales (pedido explícito del usuario: "que sea visualmente agradable"):
- Tema Docusaurus classic personalizado con paleta propia (verde/teal), soporte light/dark nativo.
- Homepage custom (no la genérica de Docusaurus): hero con título/tagline + botones ("Empezar" / "Ver en GitHub") + grilla de tarjetas (una por fase, ícono + descripción corta + link a la primera página de esa sección).
- Resaltado de código (Prism) a juego con la paleta — el código es el contenido central de cada lección.
- Admoniciones (`:::tip`, `:::warning`, `:::info`) usadas de forma consistente para notas y errores comunes.
- Buscador local (`@easyops-cn/docusaurus-search-local` o similar) — sin depender de cuenta Algolia.
- Footer con links a repo, carpeta de ejemplos, licencia.

### 3. Primer ejemplo — `examples/00-hello-world`

Proyecto Spring Boot mínimo: un controlador REST (`GET /hello`), `application.yml`, un test con `@SpringBootTest` + `MockMvc`, y README con instrucciones (`./mvnw spring-boot:run`). Sin infraestructura externa todavía — solo para validar el pipeline de CI de punta a punta.

### 4. CI/CD

- `deploy-docs.yml`: build de Docusaurus y despliegue a GitHub Pages con las Actions oficiales de GitHub (`actions/upload-pages-artifact` + `actions/deploy-pages`), sin necesitar tokens manuales. Disparado en push a `main` que afecte `docs-site/**`.
- `examples-ci.yml`: `mvn -B verify` con Java 21 (Temurin) y caché de dependencias Maven, disparado en cambios dentro de `/examples`.

### 5. Verificación de la Fase 0 (criterios de aceptación)

- Build local de Docusaurus sin enlaces rotos (chequeo estricto de broken links activado).
- El workflow de deploy corre en verde y el sitio queda accesible en la URL de GitHub Pages.
- `mvn test` pasa en `00-hello-world`, local y en CI.
- Revisión visual manual: tema claro/oscuro funciona, layout responsive en móvil (~400px), homepage se ve bien.

## Pendientes antes de implementar

1. ~~Aprobación explícita del usuario de este diseño~~ — **hecho, 2026-09-14**.
2. `git init` + primer commit — la carpeta `D:\Proyectos\spring-boot` todavía no es un repo git.
3. ~~Licencia~~ — **confirmada: MIT**.
4. Invocar la skill `writing-plans` para generar el plan de implementación de la Fase 0 (no otra skill de implementación).

## Próximos pasos al retomar

Diseño aprobado. Ejecutar el ciclo completo: `writing-plans` → (revisión/aprobación del plan) → implementación de la Fase 0.
