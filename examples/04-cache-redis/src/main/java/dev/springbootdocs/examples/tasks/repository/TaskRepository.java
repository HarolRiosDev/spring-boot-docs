package dev.springbootdocs.examples.tasks.repository;

import dev.springbootdocs.examples.tasks.model.Task;
import dev.springbootdocs.examples.tasks.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUser(User user);

    /**
     * Igual que {@code findById}, pero trae el {@code User} asociado en la misma consulta
     * ({@code join fetch}) en lugar de dejarlo como proxy perezoso.
     *
     * <p>Es la consulta que usa {@link dev.springbootdocs.examples.tasks.service.CachedTaskLookup CachedTaskLookup}: lo que devuelve acaba serializado
     * a JSON dentro de Redis, y un proxy perezoso de Hibernate no es serializable de forma
     * útil (su clase real es una subclase sintética que no existe al deserializar). Aquí la
     * carga ansiosa es deliberada y acotada a este único camino — {@code Task.user} sigue
     * siendo {@code LAZY} por defecto para todos los demás.
     */
    @Query("select t from Task t join fetch t.user where t.id = :id")
    Optional<Task> findByIdWithUser(@Param("id") Long id);
}
