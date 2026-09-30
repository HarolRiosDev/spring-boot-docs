package dev.springbootdocs.examples.tasks.repository;

import dev.springbootdocs.examples.tasks.model.TaskComment;
import dev.springbootdocs.examples.tasks.model.TaskCommentId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TaskCommentRepository extends JpaRepository<TaskComment, TaskCommentId> {

    List<TaskComment> findByIdTaskIdOrderByIdLinea(Long taskId);

    @Query("select coalesce(max(c.id.linea), 0) from TaskComment c where c.id.taskId = :taskId")
    int findMaxLinea(Long taskId);

    @Modifying
    @Query("delete from TaskComment c where c.id.taskId = :taskId")
    void deleteByTaskId(Long taskId);
}
