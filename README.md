# Spring Boot desde cero

Sitio de documentación para aprender Spring Boot desde cero, en español, con proyectos ejecutables por tema.

- 📖 Documentación: [`docs-site/`](docs-site/) — sitio [Docusaurus](https://docusaurus.io/)
- 💻 Ejemplos ejecutables: [`examples/`](examples/) — un proyecto Maven independiente por lección
- 🚀 Despliegue: GitHub Actions → GitHub Pages

## Desarrollo local del sitio

```bash
cd docs-site
npm install
npm start
```

## Ejecutar un ejemplo

```bash
cd examples/00-hello-world
./mvnw spring-boot:run
```

Requiere JDK 21+ instalado (usa `JAVA_HOME` si tu `java` por defecto es más antiguo).

## Licencia

[MIT](LICENSE)
