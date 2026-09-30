package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.CommentRequest;
import dev.springbootdocs.examples.tasks.dto.CommentResponse;
import dev.springbootdocs.examples.tasks.exception.TaskNotFoundException;
import dev.springbootdocs.examples.tasks.model.TaskComment;
import dev.springbootdocs.examples.tasks.model.TaskCommentId;
import dev.springbootdocs.examples.tasks.repository.TaskCommentRepository;
import dev.springbootdocs.examples.tasks.repository.TaskRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskCommentServiceImpl implements TaskCommentService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;

    public TaskCommentServiceImpl(TaskRepository taskRepository, TaskCommentRepository taskCommentRepository) {
        this.taskRepository = taskRepository;
        this.taskCommentRepository = taskCommentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> findByTask(Long taskId) {
        checkTaskExists(taskId);
        return taskCommentRepository.findByIdTaskIdOrderByIdLinea(taskId).stream()
                .map(CommentResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CommentResponse add(Long taskId, CommentRequest request) {
        checkTaskExists(taskId);
        // Igual que la aplicación antigua: la línea nueva es la última de la tarea + 1
        int linea = taskCommentRepository.findMaxLinea(taskId) + 1;
        TaskComment comment = new TaskComment(new TaskCommentId(taskId, linea), request.texto());
        return CommentResponse.from(taskCommentRepository.saveAndFlush(comment));
    }

    private void checkTaskExists(Long taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new TaskNotFoundException(taskId);
        }
    }
}
