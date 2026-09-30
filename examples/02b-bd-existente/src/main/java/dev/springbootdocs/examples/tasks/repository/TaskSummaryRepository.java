package dev.springbootdocs.examples.tasks.repository;

import dev.springbootdocs.examples.tasks.model.TaskSummary;
import java.util.List;
import org.springframework.data.repository.Repository;

// Repository (no JpaRepository): la vista es de solo lectura, así que no exponemos save ni delete
public interface TaskSummaryRepository extends Repository<TaskSummary, Long> {

    List<TaskSummary> findAllByOrderByIdAsc();
}
