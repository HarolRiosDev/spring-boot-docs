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
