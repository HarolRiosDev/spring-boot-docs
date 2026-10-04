package dev.springbootdocs.examples.tasks.repository;

import dev.springbootdocs.examples.tasks.model.Task;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    // No puede llamarse findAll: JpaRepository ya tiene Page<Task> findAll(Pageable)
    // y Java no permite dos métodos que solo se diferencian en el tipo de retorno
    Slice<Task> findAllBy(Pageable pageable);
}
