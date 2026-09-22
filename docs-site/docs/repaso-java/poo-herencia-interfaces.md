---
title: POO, herencia, polimorfismo e interfaces
sidebar_position: 1
---

# POO, herencia, polimorfismo e interfaces

Repaso rápido, no un curso — si ya te sientes cómodo con esto, salta directo a [Colecciones](./colecciones).

## Clases, objetos y encapsulación

Una clase describe la forma de algo; un objeto es una instancia concreta de esa forma:

```java
public class Task {
    private String titulo;
    private boolean completada;

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }
}
```

Los campos son `private` y se accede a ellos por métodos públicos (`getTitulo`/`setTitulo`) — es **encapsulación**: el objeto controla cómo se lee y se modifica su propio estado, en vez de que cualquiera manipule los campos directamente. Verás esto en cada entidad y cada DTO de los ejemplos ejecutables de este sitio.

## Herencia

Una clase puede extender otra con `extends`, heredando sus campos y métodos:

```java
public class Animal {
    protected String nombre;

    public void hacerSonido() {
        System.out.println("...");
    }
}

public class Perro extends Animal {
    @Override
    public void hacerSonido() {
        System.out.println("Guau");
    }
}
```

`super` llama a la implementación de la clase padre (`super.hacerSonido()`, o `super(nombre)` en un constructor). La herencia tiene sentido cuando hay una relación real "es un" (`Perro` **es un** `Animal`) — cuando la relación es "tiene un" (`Task` **tiene un** `User`), lo correcto es composición (un campo), no herencia. Este sitio usa herencia muy poco — los ejemplos ejecutables prefieren composición e interfaces casi siempre, que es también lo habitual en aplicaciones Spring Boot reales.

## Polimorfismo

Que dos clases distintas respondan de forma distinta al mismo método, llamado a través de un tipo común:

```java
Animal miMascota = new Perro();
miMascota.hacerSonido(); // imprime "Guau" — se ejecuta la versión de Perro, no la de Animal
```

`miMascota` está declarado como `Animal` (el tipo "de arriba"), pero contiene un `Perro` — eso es *upcasting*. Java decide en tiempo de ejecución cuál `hacerSonido()` ejecutar según el objeto real, no según el tipo de la variable.

## Interfaces

Una interfaz declara **qué** puede hacer algo, sin decir **cómo**:

```java
public interface TaskRepository {
    Task save(Task task);
    Optional<Task> findById(Long id);
}
```

Cualquier clase puede `implements TaskRepository` y proveer su propia versión de esos métodos — una respaldada por una lista en memoria, otra por una base de datos real, sin que el código que usa `TaskRepository` sepa (ni le importe) cuál es. Esto **ya lo viste en la práctica** en [Inyección de dependencias](/docs/01-fundamentos/inyeccion-dependencias): `TaskController` depende de la interfaz `TaskService`, nunca de una implementación concreta — es la razón por la que, entre la Fase 1 (una lista en memoria) y la Fase 2 (Postgres real), `TaskController.java` es **exactamente el mismo archivo**, byte a byte, aunque toda la forma de guardar los datos cambió por debajo.

## Clase abstracta vs. interfaz

Una clase abstracta (`abstract class`) puede tener campos, constructores y métodos ya implementados, además de métodos sin implementar que las subclases deben completar — útil cuando varias clases comparten código real, no solo un contrato. Una interfaz (antes de Java 8, sin ningún código) es un contrato puro. En la práctica, en Spring Boot vas a usar interfaces con muchísima más frecuencia que clases abstractas — el propio ejemplo de arriba (`TaskRepository`) es el patrón que vas a ver una y otra vez en este sitio.
