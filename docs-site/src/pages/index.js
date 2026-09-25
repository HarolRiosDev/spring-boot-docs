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
    to: '/docs/repaso-java/',
  },
  {
    icon: '🧱',
    title: '1. Fundamentos',
    description: 'DI, beans, REST, capas, validación, manejo de errores.',
    to: '/docs/01-fundamentos/',
  },
  {
    icon: '🗄️',
    title: '2. Persistencia',
    description: 'Spring Data JPA, PostgreSQL, Flyway.',
    to: '/docs/02-persistencia/',
  },
  {
    icon: '🔐',
    title: '3. Seguridad y Auth',
    description: 'Spring Security, JWT, roles.',
    to: '/docs/03-security-jwt/',
  },
  {
    icon: '⚡',
    title: '4. Caché y Redis',
    description: 'Spring Cache respaldado por Redis.',
    to: '/docs/04-cache-redis/',
  },
  {
    icon: '📨',
    title: '5. Mensajería (Kafka)',
    description: 'Productores y consumidores con Spring Kafka.',
    to: '/docs/05-kafka/',
  },
  {
    icon: '🧪',
    title: '6. Testing',
    description: 'JUnit, Mockito, Testcontainers.',
    to: '/docs/06-testing/',
  },
  {
    icon: '📊',
    title: '7. Producción',
    description: 'Actuator, métricas, logging, OpenAPI.',
    to: '/docs/07-observabilidad/',
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
          <Link className="button button--lg button--secondary" to="/docs/00-bienvenida/">
            Empezar 🚀
          </Link>
          <Link
            className="button button--lg button--outline button--secondary"
            href="https://github.com/HarolRiosDev/spring-boot-docs">
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
