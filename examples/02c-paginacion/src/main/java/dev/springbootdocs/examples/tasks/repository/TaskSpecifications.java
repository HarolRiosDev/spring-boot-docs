package dev.springbootdocs.examples.tasks.repository;

import dev.springbootdocs.examples.tasks.model.Task;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

// Cada método devuelve una condición del WHERE; el servicio combina solo las que necesita
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> conCompletada(boolean completada) {
        return (root, query, cb) -> cb.equal(root.get("completada"), completada);
    }

    // Sin distinguir mayúsculas: se compara lower(titulo) con el texto en minúsculas
    public static Specification<Task> tituloContiene(String texto) {
        String patron = "%" + escaparLike(texto.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("titulo")), patron, '\\');
    }

    // En LIKE, % y _ son comodines: sin escaparlos, buscar "%" encontraría todas las tareas
    private static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
