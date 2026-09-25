package dev.springbootdocs.examples.tasks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.springbootdocs.examples.tasks.dto.TaskResponse;
import dev.springbootdocs.examples.tasks.exception.TaskAccessDeniedException;
import dev.springbootdocs.examples.tasks.exception.TaskNotFoundException;
import dev.springbootdocs.examples.tasks.model.Role;
import dev.springbootdocs.examples.tasks.model.Task;
import dev.springbootdocs.examples.tasks.model.User;
import dev.springbootdocs.examples.tasks.repository.TaskRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CachedTaskLookup cachedTaskLookup;

    private TaskServiceImpl taskService;

    private User owner;
    private User otherUser;
    private User admin;

    @BeforeEach
    void setUp() {
        // un registro de métricas real en memoria: no hace falta mockear Micrometer
        taskService = new TaskServiceImpl(taskRepository, cachedTaskLookup, new SimpleMeterRegistry());

        owner = new User("owner", "hash", Role.USER);
        ReflectionTestUtils.setField(owner, "id", 1L);

        otherUser = new User("other", "hash", Role.USER);
        ReflectionTestUtils.setField(otherUser, "id", 2L);

        admin = new User("admin", "hash", Role.ADMIN);
        ReflectionTestUtils.setField(admin, "id", 3L);
    }

    @Test
    void findById_asOwner_returnsTask() {
        Task task = taskOwnedBy(owner, 10L);
        when(cachedTaskLookup.findById(10L)).thenReturn(task);

        TaskResponse response = taskService.findById(10L, owner);

        assertThat(response.titulo()).isEqualTo("Comprar pan");
    }

    @Test
    void findById_asDifferentUser_throwsAccessDenied() {
        Task task = taskOwnedBy(owner, 10L);
        when(cachedTaskLookup.findById(10L)).thenReturn(task);

        assertThatThrownBy(() -> taskService.findById(10L, otherUser))
                .isInstanceOf(TaskAccessDeniedException.class);
    }

    @Test
    void findById_asAdmin_returnsTaskEvenWithoutOwnership() {
        Task task = taskOwnedBy(owner, 10L);
        when(cachedTaskLookup.findById(10L)).thenReturn(task);

        TaskResponse response = taskService.findById(10L, admin);

        assertThat(response.titulo()).isEqualTo("Comprar pan");
    }

    @Test
    void findById_whenTaskDoesNotExist_throwsNotFound() {
        when(cachedTaskLookup.findById(99L)).thenThrow(new TaskNotFoundException(99L));

        assertThatThrownBy(() -> taskService.findById(99L, owner))
                .isInstanceOf(TaskNotFoundException.class);
    }

    private Task taskOwnedBy(User user, Long id) {
        Task task = new Task("Comprar pan", "desc", false, user);
        ReflectionTestUtils.setField(task, "id", id);
        return task;
    }
}
