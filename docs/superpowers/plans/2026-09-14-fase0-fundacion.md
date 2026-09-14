# Fase 0 — Fundación del repo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar el repo `D:\Proyectos\spring-boot` con un sitio Docusaurus funcional y visualmente cuidado, el primer ejemplo ejecutable (`examples/00-hello-world`), y CI/CD de punta a punta (deploy a GitHub Pages + tests de examples), listo para que las fases 1-7 y R solo añadan contenido.

**Architecture:** Monorepo con dos proyectos independientes — `docs-site/` (Docusaurus classic, JavaScript) y `examples/00-hello-world/` (Maven + Spring Boot 3, Java 21, con Maven Wrapper) — más `.github/workflows/` con un pipeline por proyecto. Cada proyecto se construye y verifica de forma aislada; no comparten build ni dependencias.

**Tech Stack:** Docusaurus 3.x (classic preset, JavaScript), `@easyops-cn/docusaurus-search-local`, Spring Boot 3.4.1, Java 21, Maven Wrapper, GitHub Actions (`actions/setup-node`, `actions/setup-java`, `actions/configure-pages`, `actions/deploy-pages`).

**Spec:** [2026-09-14-fase0-fundacion-design.md](../specs/2026-09-14-fase0-fundacion-design.md)

> **Nota de implementación (2026-09-14):** durante la ejecución de este plan, `start.spring.io` ya no ofrecía ninguna versión 3.x de Spring Boot (línea disponible `>=4.0.0`). El ejemplo `00-hello-world` se generó con `bootVersion=4.1.1` en su lugar. Dos consecuencias a tener en cuenta para fases futuras: (1) la versión del parent en `pom.xml` es `4.1.1` sin sufijo `.RELEASE` (ese sufijo se eliminó desde Spring Boot 2.0, no es específico de la 4.x); (2) `@AutoConfigureMockMvc` se movió de `org.springframework.boot.test.autoconfigure.web.servlet` a `org.springframework.boot.webmvc.test.autoconfigure`. Al generar el ejemplo de una fase futura, comprobar en start.spring.io la versión estable actual en vez de copiar literalmente `bootVersion=3.4.1` de los pasos de abajo.

## Global Constraints

- Generador del sitio: **Docusaurus** (JavaScript, no TypeScript).
- Idioma del contenido: **español**; código y términos técnicos en inglés.
- Build tool de los ejemplos: **Maven** (con Maven Wrapper). Versión de Java: **21 LTS**.
- Estructura de `/examples`: proyectos **independientes y autocontenidos** por tema (sin reactor multi-módulo).
- Despliegue del sitio: **GitHub Pages vía GitHub Actions** (no hosting de una app corriendo).
- Requisito visual explícito: paleta propia **verde/teal**, homepage custom (hero + tarjetas), no el theme genérico sin tocar.
- `onBrokenLinks: 'throw'` — build del sitio debe fallar si hay enlaces rotos.
- Licencia: **MIT**.
- `organizationName`/`projectName`/URLs de GitHub quedan como **placeholder `TU_USUARIO` / `spring-boot-docs`** — el usuario no tiene el repo remoto creado todavía; deben actualizarse antes de hacer push real y activar Pages.
- Máquina de desarrollo: `java` en PATH resuelve a 1.8, pero hay un JDK 21+ real en `C:\jdk-23.0.1` (`JAVA_HOME` ya apunta ahí). Todos los pasos que ejecutan `mvnw` deben asumir que `JAVA_HOME=C:\jdk-23.0.1` (o un JDK 21+ equivalente) está activo en la sesión de shell, no el `java` del PATH por defecto.

---

## File Structure

```
/
├── .gitignore
├── README.md
├── LICENSE
├── docs-site/                          # Docusaurus (classic, JS)
│   ├── docusaurus.config.js
│   ├── sidebars.js
│   ├── package.json
│   ├── src/
│   │   ├── css/custom.css              # paleta verde/teal
│   │   └── pages/
│   │       ├── index.js                # homepage custom (hero + tarjetas)
│   │       └── index.module.css
│   ├── docs/
│   │   ├── 00-bienvenida/{_category_.json, index.md}
│   │   ├── repaso-java/{_category_.json, index.md}
│   │   ├── 01-fundamentos/{_category_.json, index.md}
│   │   ├── 02-persistencia/{_category_.json, index.md}
│   │   ├── 03-security-jwt/{_category_.json, index.md}
│   │   ├── 04-cache-redis/{_category_.json, index.md}
│   │   ├── 05-kafka/{_category_.json, index.md}
│   │   ├── 06-testing/{_category_.json, index.md}
│   │   └── 07-observabilidad/{_category_.json, index.md}
│   └── static/img/...                  # assets por defecto del scaffold
├── examples/
│   └── 00-hello-world/
│       ├── pom.xml
│       ├── mvnw / mvnw.cmd / .mvn/wrapper/...
│       ├── src/main/java/dev/springbootdocs/examples/hello/HelloWorldApplication.java
│       ├── src/main/java/dev/springbootdocs/examples/hello/HelloController.java
│       ├── src/main/resources/application.yml
│       ├── src/test/java/dev/springbootdocs/examples/hello/HelloControllerTest.java
│       └── README.md
└── .github/workflows/
    ├── deploy-docs.yml
    └── examples-ci.yml
```

---

### Task 1: Inicializar el repo git y archivos raíz

**Files:**
- Create: `D:\Proyectos\spring-boot\.gitignore`
- Create: `D:\Proyectos\spring-boot\README.md`
- Create: `D:\Proyectos\spring-boot\LICENSE`

**Interfaces:**
- Produces: repo git inicializado en rama `main`, con `.gitignore` que las tareas 2 y 6 asumen ya existente (rutas `docs-site/build/`, `docs-site/.docusaurus/`, `examples/**/target/`, `node_modules/`).

- [ ] **Step 1: `git init`**

```bash
cd /d/Proyectos/spring-boot
git init -b main
```

- [ ] **Step 2: Crear `.gitignore`**

```gitignore
# Node / Docusaurus
node_modules/
docs-site/build/
docs-site/.docusaurus/
docs-site/.cache-loader/

# Maven
examples/**/target/

# IDE
.idea/
*.iml
.vscode/

# OS
.DS_Store
Thumbs.db
```

- [ ] **Step 3: Crear `README.md` (raíz del repo)**

```markdown
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
```

- [ ] **Step 4: Crear `LICENSE` (MIT)**

```
MIT License

Copyright (c) 2026 TU_USUARIO

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 5: Verificar y commitear**

```bash
git status
git add .gitignore README.md LICENSE
git commit -m "chore: initialize repo structure"
```

Expected: commit creado, `git log --oneline` muestra 1 commit.

---

### Task 2: Scaffold del sitio Docusaurus

**Files:**
- Create: `docs-site/` (generado por `create-docusaurus`)

**Interfaces:**
- Consumes: `.gitignore` de Task 1 (ya ignora `docs-site/build`, `docs-site/.docusaurus`, `node_modules`).
- Produces: proyecto Docusaurus classic en JS con `package.json`, `docusaurus.config.js`, `sidebars.js`, `src/`, `docs/`, `static/` por defecto — que las Tasks 3-5 van a modificar.

- [ ] **Step 1: Generar el scaffold**

```bash
cd /d/Proyectos/spring-boot
npx create-docusaurus@latest docs-site classic --javascript --package-manager npm
```

- [ ] **Step 2: Verificar que el scaffold por defecto compila**

```bash
cd docs-site
npm run build
```

Expected: `[SUCCESS] Generated static files in "build".` sin errores.

- [ ] **Step 3: Commit**

```bash
cd /d/Proyectos/spring-boot
git add docs-site
git commit -m "chore: scaffold docusaurus site"
```

---

### Task 3: Configurar `docusaurus.config.js` y `sidebars.js`

**Files:**
- Modify: `docs-site/docusaurus.config.js`
- Modify: `docs-site/sidebars.js`
- Delete: `docs-site/blog/` (no se usa; el sitio no tiene blog)
- Modify: `docs-site/package.json` (nueva dependencia del plugin de búsqueda)

**Interfaces:**
- Consumes: scaffold de Task 2.
- Produces: `sidebarPath: './sidebars.js'` con `tutorialSidebar` autogenerado desde `docs/`, que Task 4 puebla; navbar/footer con links a `/docs/00-bienvenida` que Task 4 debe crear con ese id exacto.

- [ ] **Step 1: Instalar el plugin de búsqueda local**

```bash
cd docs-site
npm install @easyops-cn/docusaurus-search-local
```

- [ ] **Step 2: Borrar el blog por defecto (no se usa)**

```bash
rm -rf blog
```

- [ ] **Step 3: Reescribir `docusaurus.config.js`**

```js
// @ts-check
const {themes: prismThemes} = require('prism-react-renderer');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Spring Boot desde cero',
  tagline: 'Documentación clara y proyectos ejecutables para aprender Spring Boot',
  favicon: 'img/favicon.ico',

  future: {
    v4: true,
  },

  url: 'https://TU_USUARIO.github.io',
  baseUrl: '/spring-boot-docs/',

  organizationName: 'TU_USUARIO',
  projectName: 'spring-boot-docs',

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  i18n: {
    defaultLocale: 'es',
    locales: ['es'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: './sidebars.js',
          editUrl: 'https://github.com/TU_USUARIO/spring-boot-docs/tree/main/docs-site/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],

  plugins: [
    [
      '@easyops-cn/docusaurus-search-local',
      /** @type {import('@easyops-cn/docusaurus-search-local').PluginOptions} */
      ({
        hashed: true,
        language: ['es'],
        indexDocs: true,
        indexBlog: false,
        docsRouteBasePath: '/docs',
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/docusaurus-social-card.jpg',
      navbar: {
        title: 'Spring Boot desde cero',
        logo: {
          alt: 'Logo',
          src: 'img/logo.svg',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'tutorialSidebar',
            position: 'left',
            label: 'Documentación',
          },
          {
            href: 'https://github.com/TU_USUARIO/spring-boot-docs',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Empezar',
                to: '/docs/00-bienvenida',
              },
            ],
          },
          {
            title: 'Proyecto',
            items: [
              {
                label: 'Repositorio',
                href: 'https://github.com/TU_USUARIO/spring-boot-docs',
              },
              {
                label: 'Ejemplos ejecutables',
                href: 'https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples',
              },
              {
                label: 'Licencia (MIT)',
                href: 'https://github.com/TU_USUARIO/spring-boot-docs/blob/main/LICENSE',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} Spring Boot desde cero.`,
      },
      prism: {
        theme: prismThemes.oneLight,
        darkTheme: prismThemes.oneDark,
      },
    }),
};

module.exports = config;
```

- [ ] **Step 4: Reescribir `sidebars.js`**

```js
/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  tutorialSidebar: [{type: 'autogenerated', dirName: '.'}],
};

module.exports = sidebars;
```

- [ ] **Step 5: Verificar build**

```bash
npm run build
```

Expected: build falla en este punto porque `docs/intro.md` y `docs/tutorial-*` del scaffold por defecto todavía apuntan a rutas que Task 4 va a reemplazar, o compila pero con el contenido genérico — cualquiera de los dos es aceptable aquí; el chequeo estricto real (`onBrokenLinks: 'throw'`) se valida al final de Task 4.

- [ ] **Step 6: Commit**

```bash
cd /d/Proyectos/spring-boot
git add docs-site
git commit -m "feat: configure docusaurus site (nav, footer, search, strict broken-links)"
```

---

### Task 4: Estructura de contenido y sidebar completo

**Files:**
- Delete: `docs-site/docs/intro.md`, `docs-site/docs/tutorial-basics/`, `docs-site/docs/tutorial-extras/`
- Create: `docs-site/docs/00-bienvenida/_category_.json`, `docs-site/docs/00-bienvenida/index.md`
- Create: `docs-site/docs/repaso-java/_category_.json`, `docs-site/docs/repaso-java/index.md`
- Create: `docs-site/docs/01-fundamentos/_category_.json`, `docs-site/docs/01-fundamentos/index.md`
- Create: `docs-site/docs/02-persistencia/_category_.json`, `docs-site/docs/02-persistencia/index.md`
- Create: `docs-site/docs/03-security-jwt/_category_.json`, `docs-site/docs/03-security-jwt/index.md`
- Create: `docs-site/docs/04-cache-redis/_category_.json`, `docs-site/docs/04-cache-redis/index.md`
- Create: `docs-site/docs/05-kafka/_category_.json`, `docs-site/docs/05-kafka/index.md`
- Create: `docs-site/docs/06-testing/_category_.json`, `docs-site/docs/06-testing/index.md`
- Create: `docs-site/docs/07-observabilidad/_category_.json`, `docs-site/docs/07-observabilidad/index.md`

**Interfaces:**
- Consumes: `sidebars.js` autogenerado de Task 3 (usa `position` de cada `_category_.json` para el orden).
- Produces: ruta `/docs/00-bienvenida` que el navbar/footer de Task 3 y la homepage de Task 5 enlazan.

- [ ] **Step 1: Borrar contenido de ejemplo del scaffold**

```bash
cd docs-site/docs
rm -f intro.md
rm -rf tutorial-basics tutorial-extras
```

- [ ] **Step 2: Crear `00-bienvenida`**

`docs-site/docs/00-bienvenida/_category_.json`:
```json
{
  "label": "🏠 Bienvenida",
  "position": 1
}
```

`docs-site/docs/00-bienvenida/index.md`:
```markdown
---
title: Bienvenida
---

# Spring Boot desde cero

Este sitio te enseña Spring Boot de forma práctica: cada tema tiene su documentación en español y, casi siempre, un **proyecto Spring Boot ejecutable** que puedes clonar y correr en tu máquina.

## ¿Para quién es esto?

Está pensado para alguien que **ya sabe Java** (POO, colecciones, algo de lambdas/streams) pero es **nuevo en Spring Boot**. Si necesitas repasar Java primero, hay una sección opcional para eso.

## Cómo está organizado

- 📖 La documentación de cada tema vive en este sitio, en el menú de la izquierda.
- 💻 Los ejemplos ejecutables viven en la carpeta [`examples/`](https://github.com/TU_USUARIO/spring-boot-docs/tree/main/examples) del repositorio — un proyecto Maven independiente por tema, para que puedas clonar solo la carpeta que te interesa.

## Ruta sugerida

| Fase | Tema | Proyecto ejecutable |
|---|---|---|
| ☕ Repaso (opcional) | POO, herencia, polimorfismo, interfaces, colecciones, lambdas/streams | — |
| 1 | Fundamentos: DI, beans, REST, capas, validación, manejo de errores | `01-fundamentos` |
| 2 | Persistencia: Spring Data JPA, Postgres, Flyway | `02-persistencia` |
| 3 | Seguridad y Auth: Spring Security, JWT, roles | `03-security-jwt` |
| 4 | Caché y Redis | `04-cache-redis` |
| 5 | Mensajería con Kafka | `05-kafka` |
| 6 | Testing: JUnit, Mockito, Testcontainers | `06-testing` |
| 7 | Producción: Actuator, métricas, logging, Docker | `07-observabilidad` |

Cada sección todavía en construcción lo indica claramente — el índice completo ya está aquí para que veas el mapa completo desde el día uno.

Empieza por **Fundamentos** en el menú, o por el **Repaso de Java** si quieres afianzar la base primero.
```

- [ ] **Step 3: Crear `repaso-java`**

`docs-site/docs/repaso-java/_category_.json`:
```json
{
  "label": "☕ Repaso de Java (opcional)",
  "position": 2
}
```

`docs-site/docs/repaso-java/index.md`:
```markdown
---
title: Repaso de Java
---

# Repaso de Java (opcional)

🚧 **En construcción.**

Esta sección es opcional: repasa POO, herencia, polimorfismo, interfaces, colecciones y lambdas/streams en Java puro, sin infraestructura ni proyecto ejecutable — solo para afianzar la base antes de entrar en Spring Boot.

Si ya te sientes cómodo con estos temas, puedes saltar directo a **Fundamentos**.
```

- [ ] **Step 4: Crear placeholders `01-fundamentos` a `07-observabilidad`**

Para cada una de las siguientes 7 secciones, crear `_category_.json` e `index.md` con el patrón exacto de abajo (cambiando `label`, `position`, `title` y `temas`):

| Carpeta | `label` | `position` | `title` | `temas` |
|---|---|---|---|---|
| `01-fundamentos` | `🧱 1. Fundamentos` | 3 | Fundamentos | inyección de dependencias, beans, controladores REST, capas (controller/service/repository), validación, manejo de errores |
| `02-persistencia` | `🗄️ 2. Persistencia` | 4 | Persistencia | Spring Data JPA, PostgreSQL, migraciones con Flyway, docker-compose |
| `03-security-jwt` | `🔐 3. Seguridad y Auth` | 5 | Seguridad y Auth | Spring Security, autenticación con JWT, roles y permisos |
| `04-cache-redis` | `⚡ 4. Caché y Redis` | 6 | Caché y Redis | Spring Cache, Redis como backend de caché |
| `05-kafka` | `📨 5. Mensajería (Kafka)` | 7 | Mensajería con Kafka | productores y consumidores con Spring Kafka |
| `06-testing` | `🧪 6. Testing` | 8 | Testing | JUnit 5, Mockito, Testcontainers |
| `07-observabilidad` | `📊 7. Producción` | 9 | Producción | Actuator, métricas, logging, Docker |

`_category_.json` (plantilla, sustituir `label` y `position`):
```json
{
  "label": "<label de la fila>",
  "position": <position de la fila>
}
```

`index.md` (plantilla, sustituir `title` y `temas`):
```markdown
---
title: <title de la fila>
---

# <title de la fila>

🚧 **En construcción.**

Esta sección cubrirá: <temas de la fila>.

Vuelve pronto — este tema se desarrolla en una fase posterior del roadmap. Mientras tanto, puedes ver el [índice completo](/docs/00-bienvenida) del sitio.
```

Crear los 7 pares de archivos con estos valores concretos (no dejar ninguno sin crear).

- [ ] **Step 5: Verificar build sin enlaces rotos**

```bash
cd docs-site
npm run build
```

Expected: `[SUCCESS] Generated static files in "build".` — si hay un link roto (por ejemplo a `/docs/00-bienvenida` mal escrito), el build falla por `onBrokenLinks: 'throw'`; corregir hasta que pase.

- [ ] **Step 6: Commit**

```bash
cd /d/Proyectos/spring-boot
git add docs-site
git commit -m "docs: scaffold full documentation structure with placeholder sections"
```

---

### Task 5: Homepage custom y paleta verde/teal

**Files:**
- Modify: `docs-site/src/css/custom.css`
- Modify: `docs-site/src/pages/index.js`
- Modify: `docs-site/src/pages/index.module.css`
- Delete: `docs-site/src/components/HomepageFeatures/` (componente de ejemplo del scaffold, ya no se usa)

**Interfaces:**
- Consumes: rutas `/docs/00-bienvenida`, `/docs/repaso-java`, `/docs/01-fundamentos` … `/docs/07-observabilidad` creadas en Task 4.
- Produces: homepage en `/` con hero + grilla de tarjetas, sin dependencias nuevas.

- [ ] **Step 1: Borrar el componente de ejemplo del scaffold**

```bash
cd docs-site/src
rm -rf components/HomepageFeatures
```

- [ ] **Step 2: Reescribir `src/css/custom.css`**

```css
/**
 * Paleta verde/teal — light y dark.
 */

:root {
  --ifm-color-primary: #0d9488;
  --ifm-color-primary-dark: #0c8377;
  --ifm-color-primary-darker: #0b7b70;
  --ifm-color-primary-darkest: #09655c;
  --ifm-color-primary-light: #0eab9d;
  --ifm-color-primary-lighter: #0fb3a4;
  --ifm-color-primary-lightest: #13cbba;
  --ifm-code-font-size: 95%;
  --docusaurus-highlighted-code-line-bg: rgba(13, 148, 136, 0.1);
}

[data-theme='dark'] {
  --ifm-color-primary: #2dd4bf;
  --ifm-color-primary-dark: #22c6b1;
  --ifm-color-primary-darker: #1fbba7;
  --ifm-color-primary-darkest: #199a89;
  --ifm-color-primary-light: #3ddbc7;
  --ifm-color-primary-lighter: #4bdfcc;
  --ifm-color-primary-lightest: #71e7d9;
  --docusaurus-highlighted-code-line-bg: rgba(45, 212, 191, 0.15);
}
```

- [ ] **Step 3: Reescribir `src/pages/index.module.css`**

```css
.heroBanner {
  padding: 5rem 0;
  text-align: center;
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #0d9488 0%, #134e4a 100%);
  color: #fff;
}

[data-theme='dark'] .heroBanner {
  background: linear-gradient(135deg, #134e4a 0%, #042f2e 100%);
}

.heroButtons {
  display: flex;
  gap: 1rem;
  justify-content: center;
  flex-wrap: wrap;
  margin-top: 1.5rem;
}

.cardsSection {
  padding: 3rem 1rem;
}

.cardsGrid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 1.5rem;
  max-width: 1100px;
  margin: 0 auto;
}

.card {
  display: block;
  padding: 1.5rem;
  border-radius: 12px;
  border: 1px solid var(--ifm-color-emphasis-200);
  background: var(--ifm-background-surface-color);
  text-decoration: none;
  color: inherit;
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease;
}

.card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(13, 148, 136, 0.15);
  border-color: var(--ifm-color-primary);
  text-decoration: none;
  color: inherit;
}

.cardIcon {
  font-size: 2rem;
  display: block;
  margin-bottom: 0.5rem;
}

.cardTitle {
  margin: 0 0 0.4rem;
  font-size: 1.1rem;
}

.cardDescription {
  margin: 0;
  font-size: 0.9rem;
  color: var(--ifm-color-emphasis-700);
}
```

- [ ] **Step 4: Reescribir `src/pages/index.js`**

```jsx
import React from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import styles from './index.module.css';

const PHASES = [
  {
    icon: '☕',
    title: 'Repaso de Java (opcional)',
    description: 'POO, herencia, polimorfismo, interfaces, colecciones, lambdas/streams.',
    to: '/docs/repaso-java',
  },
  {
    icon: '🧱',
    title: '1. Fundamentos',
    description: 'DI, beans, REST, capas, validación, manejo de errores.',
    to: '/docs/01-fundamentos',
  },
  {
    icon: '🗄️',
    title: '2. Persistencia',
    description: 'Spring Data JPA, PostgreSQL, Flyway.',
    to: '/docs/02-persistencia',
  },
  {
    icon: '🔐',
    title: '3. Seguridad y Auth',
    description: 'Spring Security, JWT, roles.',
    to: '/docs/03-security-jwt',
  },
  {
    icon: '⚡',
    title: '4. Caché y Redis',
    description: 'Spring Cache respaldado por Redis.',
    to: '/docs/04-cache-redis',
  },
  {
    icon: '📨',
    title: '5. Mensajería (Kafka)',
    description: 'Productores y consumidores con Spring Kafka.',
    to: '/docs/05-kafka',
  },
  {
    icon: '🧪',
    title: '6. Testing',
    description: 'JUnit, Mockito, Testcontainers.',
    to: '/docs/06-testing',
  },
  {
    icon: '📊',
    title: '7. Producción',
    description: 'Actuator, métricas, logging, Docker.',
    to: '/docs/07-observabilidad',
  },
];

function HomepageHeader() {
  return (
    <header className={styles.heroBanner}>
      <div className="container">
        <Heading as="h1" className="hero__title">
          Spring Boot desde cero
        </Heading>
        <p className="hero__subtitle">
          Documentación clara y proyectos ejecutables para aprender Spring Boot
        </p>
        <div className={styles.heroButtons}>
          <Link className="button button--lg button--secondary" to="/docs/00-bienvenida">
            Empezar 🚀
          </Link>
          <Link
            className="button button--lg button--outline button--secondary"
            href="https://github.com/TU_USUARIO/spring-boot-docs">
            Ver en GitHub
          </Link>
        </div>
      </div>
    </header>
  );
}

function PhaseCards() {
  return (
    <section className={styles.cardsSection}>
      <div className={clsx('container', styles.cardsGrid)}>
        {PHASES.map((phase) => (
          <Link key={phase.to} to={phase.to} className={styles.card}>
            <span className={styles.cardIcon}>{phase.icon}</span>
            <h3 className={styles.cardTitle}>{phase.title}</h3>
            <p className={styles.cardDescription}>{phase.description}</p>
          </Link>
        ))}
      </div>
    </section>
  );
}

export default function Home() {
  return (
    <Layout
      title="Spring Boot desde cero"
      description="Documentación clara y proyectos ejecutables para aprender Spring Boot">
      <HomepageHeader />
      <main>
        <PhaseCards />
      </main>
    </Layout>
  );
}
```

- [ ] **Step 5: Verificar visualmente**

```bash
npm start
```

Expected: sitio abre en `http://localhost:3000`, hero con gradiente teal y botones, 8 tarjetas debajo enlazando a cada sección, tema oscuro (toggle en navbar) también se ve coherente. Cerrar el servidor (`Ctrl+C`) tras verificar.

- [ ] **Step 6: Verificar build de producción**

```bash
npm run build
```

Expected: build exitoso, sin errores de import roto por el componente `HomepageFeatures` eliminado.

- [ ] **Step 7: Commit**

```bash
cd /d/Proyectos/spring-boot
git add docs-site
git commit -m "feat: custom homepage and teal/green theme"
```

---

### Task 6: Ejemplo ejecutable `examples/00-hello-world`

**Files:**
- Create: `examples/00-hello-world/` (scaffold vía Spring Initializr + wrapper)
- Modify: `examples/00-hello-world/pom.xml`
- Create: `examples/00-hello-world/src/main/java/dev/springbootdocs/examples/hello/HelloController.java`
- Create: `examples/00-hello-world/src/main/resources/application.yml`
- Delete: `examples/00-hello-world/src/main/resources/application.properties`
- Create: `examples/00-hello-world/src/test/java/dev/springbootdocs/examples/hello/HelloControllerTest.java`
- Create: `examples/00-hello-world/README.md`

**Interfaces:**
- Consumes: `.gitignore` de Task 1 (ignora `examples/**/target/`).
- Produces: `GET /hello` devolviendo `"¡Hola desde Spring Boot!"`, que Task 8 (CI) ejecuta vía `./mvnw -B verify`.

- [ ] **Step 1: Generar el proyecto base desde Spring Initializr (incluye Maven Wrapper funcional)**

```bash
mkdir -p /d/Proyectos/spring-boot/examples
cd /d/Proyectos/spring-boot/examples
curl https://start.spring.io/starter.zip \
  -d dependencies=web \
  -d type=maven-project \
  -d language=java \
  -d bootVersion=3.4.1 \
  -d javaVersion=21 \
  -d groupId=dev.springbootdocs.examples \
  -d artifactId=hello-world \
  -d name=HelloWorld \
  -d packageName=dev.springbootdocs.examples.hello \
  -o hello-world.zip
```

- [ ] **Step 2: Descomprimir en la carpeta final `00-hello-world` y limpiar el zip**

```bash
mkdir -p 00-hello-world
cd 00-hello-world
unzip -o ../hello-world.zip
rm ../hello-world.zip
```

- [ ] **Step 3: Verificar que el wrapper funciona (usando el JDK 21+ real, no el del PATH)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -v
```

Expected: imprime versión de Apache Maven y `Java version: 23...` (o el JDK 21+ disponible), sin errores de descarga.

- [ ] **Step 4: Convertir `application.properties` a `application.yml`**

```bash
rm src/main/resources/application.properties
```

Crear `src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: 00-hello-world
```

- [ ] **Step 5: Escribir el test que falla primero (`HelloControllerTest`)**

Crear `src/test/java/dev/springbootdocs/examples/hello/HelloControllerTest.java`:
```java
package dev.springbootdocs.examples.hello;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void helloReturnsGreeting() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("¡Hola desde Spring Boot!"));
    }
}
```

- [ ] **Step 6: Ejecutar el test y verificar que falla**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=HelloControllerTest
```

Expected: FAIL — `404 Not Found` (no existe todavía `/hello`).

- [ ] **Step 7: Implementar `HelloController`**

Crear `src/main/java/dev/springbootdocs/examples/hello/HelloController.java`:
```java
package dev.springbootdocs.examples.hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "¡Hola desde Spring Boot!";
    }
}
```

- [ ] **Step 8: Ejecutar el test y verificar que pasa**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw test -Dtest=HelloControllerTest
```

Expected: PASS.

- [ ] **Step 9: Ejecutar la suite completa (incluye el test de contexto que genera Initializr)**

```bash
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B verify
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 10: Escribir `README.md` del ejemplo**

```markdown
# 00-hello-world

Primer ejemplo ejecutable del sitio **Spring Boot desde cero** — valida el pipeline de principio a fin (no tiene infraestructura externa).

## Requisitos

- JDK 21 o superior.

## Ejecutar

```bash
./mvnw spring-boot:run
```

Luego visita [http://localhost:8080/hello](http://localhost:8080/hello).

## Tests

```bash
./mvnw test
```
```

- [ ] **Step 11: Commit**

```bash
cd /d/Proyectos/spring-boot
git add examples/00-hello-world
git commit -m "feat: add 00-hello-world executable example"
```

---

### Task 7: Workflow de deploy del sitio a GitHub Pages

**Files:**
- Create: `.github/workflows/deploy-docs.yml`

**Interfaces:**
- Consumes: `docs-site/package.json` (Task 2) y `docs-site/build` como salida del build (Task 3-5).

- [ ] **Step 1: Crear `.github/workflows/deploy-docs.yml`**

```yaml
name: Deploy Docs

on:
  push:
    branches: [main]
    paths:
      - 'docs-site/**'
      - '.github/workflows/deploy-docs.yml'
  workflow_dispatch:

permissions:
  contents: read
  pages: write
  id-token: write

concurrency:
  group: pages
  cancel-in-progress: false

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 22
          cache: npm
          cache-dependency-path: docs-site/package-lock.json
      - name: Install dependencies
        working-directory: docs-site
        run: npm ci
      - name: Build site
        working-directory: docs-site
        run: npm run build
      - uses: actions/configure-pages@v5
      - uses: actions/upload-pages-artifact@v3
        with:
          path: docs-site/build

  deploy:
    needs: build
    runs-on: ubuntu-latest
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
```

- [ ] **Step 2: Validar sintaxis YAML localmente**

```bash
npx -y js-yaml .github/workflows/deploy-docs.yml
```

Expected: imprime el YAML parseado sin errores (sin excepción de parseo).

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/deploy-docs.yml
git commit -m "ci: add GitHub Pages deploy workflow for docs-site"
```

**Nota (no bloqueante para este task):** este workflow solo se ejecuta de verdad cuando exista el repo remoto en GitHub con Pages configurado como fuente "GitHub Actions" — eso queda para cuando el usuario cree el repo y haga push (ver Task 9).

---

### Task 8: Workflow de CI para `examples/`

**Files:**
- Create: `.github/workflows/examples-ci.yml`

**Interfaces:**
- Consumes: `examples/00-hello-world/pom.xml` y wrapper de Task 6. Diseñado con matriz para que las fases 1-7 solo añadan un elemento a `matrix.example`.

- [ ] **Step 1: Crear `.github/workflows/examples-ci.yml`**

```yaml
name: Examples CI

on:
  push:
    branches: [main]
    paths:
      - 'examples/**'
      - '.github/workflows/examples-ci.yml'
  pull_request:
    paths:
      - 'examples/**'
      - '.github/workflows/examples-ci.yml'

jobs:
  build:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        example:
          - 00-hello-world
    defaults:
      run:
        working-directory: examples/${{ matrix.example }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
          cache-dependency-path: examples/${{ matrix.example }}/pom.xml
      - name: Run tests
        run: ./mvnw -B verify
```

- [ ] **Step 2: Validar sintaxis YAML localmente**

```bash
npx -y js-yaml .github/workflows/examples-ci.yml
```

Expected: imprime el YAML parseado sin errores.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/examples-ci.yml
git commit -m "ci: add examples build/test workflow"
```

---

### Task 9: Verificación final end-to-end de la Fase 0

**Files:** ninguno nuevo — solo verificación y ajustes finales.

- [ ] **Step 1: Build limpio del sitio, sin enlaces rotos**

```bash
cd /d/Proyectos/spring-boot/docs-site
rm -rf build .docusaurus
npm run build
```

Expected: `[SUCCESS] Generated static files in "build".`

- [ ] **Step 2: Tests del ejemplo, limpios**

```bash
cd /d/Proyectos/spring-boot/examples/00-hello-world
JAVA_HOME=/c/jdk-23.0.1 ./mvnw -B clean verify
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Revisión visual manual (criterio de aceptación de la Fase 0)**

```bash
cd /d/Proyectos/spring-boot/docs-site
npm start
```

Verificar en el navegador:
- Homepage: hero + 8 tarjetas se ven bien.
- Toggle de tema claro/oscuro funciona y la paleta teal se ve coherente en ambos.
- Layout responsive: reducir el ancho de la ventana (~400px) y confirmar que no hay scroll horizontal y las tarjetas apilan en una columna.
- Sidebar: el orden coincide con el roadmap (Bienvenida → Repaso de Java → Fundamentos → … → Producción).
- Buscador local funciona (probar una palabra de `00-bienvenida`).

Cerrar el servidor tras verificar.

- [ ] **Step 4: Commit final de la Fase 0 (si quedó algo pendiente de las verificaciones anteriores)**

```bash
cd /d/Proyectos/spring-boot
git status
git add -A
git commit -m "chore: Fase 0 complete — docusaurus site + hello-world example + CI" --allow-empty
```

- [ ] **Step 5: Pendientes explícitos para el usuario (no automatizables desde aquí)**

Documentar/recordar al usuario, sin ejecutarlo:
1. Crear el repositorio remoto en GitHub.
2. Reemplazar `TU_USUARIO` por el usuario/organización real en: `docusaurus.config.js` (url, organizationName, projectName, editUrl, todos los `href` a GitHub), `README.md` si aplica, y `LICENSE` (nombre del copyright).
3. `git remote add origin <url>` y `git push -u origin main`.
4. En GitHub → Settings → Pages, configurar "Source: GitHub Actions".
5. Confirmar que `deploy-docs.yml` corre en verde y el sitio queda accesible.

---

## Self-Review

**Cobertura del spec:** estructura de repo (Task 1-2), Docusaurus + paleta + homepage (Task 3-5), primer ejemplo con MockMvc (Task 6), ambos workflows de CI/CD (Task 7-8), criterios de aceptación — build sin broken links, `mvn test` en CI, revisión visual (Task 9). Licencia MIT (Task 1). Sidebar completo con las 9 secciones incluyendo repaso opcional (Task 4). Todo cubierto.

**Placeholders:** el único placeholder deliberado es `TU_USUARIO`/`spring-boot-docs` en URLs de GitHub — documentado explícitamente en Global Constraints y como pendiente final en Task 9, no es un "TBD" de contenido o código.

**Consistencia de tipos/nombres:** `HelloController.hello()` → `/hello` usado igual en Task 6 (implementación) y su test; rutas `/docs/00-bienvenida`, `/docs/repaso-java`, `/docs/0N-*` usadas de forma consistente entre Task 4 (creación), Task 3 (footer) y Task 5 (homepage); `JAVA_HOME=/c/jdk-23.0.1` usado consistentemente en todos los pasos que invocan `./mvnw`.
