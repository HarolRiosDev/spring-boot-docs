package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.TaskRequest;
import dev.springbootdocs.examples.tasks.exception.InvalidSortException;
import dev.springbootdocs.examples.tasks.exception.TaskNotFoundException;
import dev.springbootdocs.examples.tasks.model.Task;
import dev.springbootdocs.examples.tasks.repository.TaskRepository;
import dev.springbootdocs.examples.tasks.repository.TaskSpecifications;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TaskServiceImpl implements TaskService {

    // Campos por los que el cliente puede ordenar. Una List y no un Set.of,
    // porque el mensaje de error los enumera y Set.of no garantiza el orden
    private static final List<String> CAMPOS_ORDENABLES = List.of("fechaCreacion", "titulo", "completada", "id");

    private static final Sort ORDEN_RECIENTES = Sort.by(Sort.Direction.DESC, "fechaCreacion", "id");

    private final TaskRepository taskRepository;

    public TaskServiceImpl(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override
    @Transactional
    public Task create(TaskRequest request) {
        Task task = new Task(request.titulo(), request.descripcion(), request.completada());
        return taskRepository.save(task);
    }

    @Override
    public Page<Task> findAll(Boolean completada, String q, Pageable pageable) {
        validarOrden(pageable.getSort());

        // Se empieza sin condiciones y se añade solo lo que venga en la petición
        Specification<Task> spec = Specification.unrestricted();
        if (completada != null) {
            spec = spec.and(TaskSpecifications.conCompletada(completada));
        }
        if (StringUtils.hasText(q)) {
            spec = spec.and(TaskSpecifications.tituloContiene(q.trim()));
        }
        return taskRepository.findAll(spec, conDesempate(pageable));
    }

    @Override
    public Slice<Task> findRecientes(Pageable pageable) {
        // El orden es parte del significado de "recientes": se ignora el sort del cliente
        Pageable recientes = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ORDEN_RECIENTES);
        return taskRepository.findAllBy(recientes);
    }

    @Override
    public Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Override
    @Transactional
    public Task update(Long id, TaskRequest request) {
        Task task = findById(id);
        task.setTitulo(request.titulo());
        task.setDescripcion(request.descripcion());
        task.setCompletada(request.completada());
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Task task = findById(id);
        taskRepository.delete(task);
    }

    private void validarOrden(Sort sort) {
        for (Sort.Order order : sort) {
            if (!CAMPOS_ORDENABLES.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), CAMPOS_ORDENABLES);
            }
        }
    }

    // Si dos tareas empatan en el campo de orden, la base de datos puede devolverlas en
    // cualquier orden, y una tarea puede repetirse o perderse entre páginas. El id es
    // único, así que añadirlo al final deja un orden estable. Va aquí y no en @SortDefault
    // porque, si el cliente manda su propio sort, Spring descarta el de la anotación entero.
    private Pageable conDesempate(Pageable pageable) {
        Sort sort = pageable.getSort();
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
