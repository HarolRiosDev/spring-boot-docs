package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.DailyTaskCount;
import dev.springbootdocs.examples.tasks.dto.TaskSummaryResponse;
import dev.springbootdocs.examples.tasks.repository.TaskJdbcRepository;
import dev.springbootdocs.examples.tasks.repository.TaskSummaryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskReportServiceImpl implements TaskReportService {

    private final TaskSummaryRepository taskSummaryRepository;
    private final TaskJdbcRepository taskJdbcRepository;

    public TaskReportServiceImpl(TaskSummaryRepository taskSummaryRepository, TaskJdbcRepository taskJdbcRepository) {
        this.taskSummaryRepository = taskSummaryRepository;
        this.taskJdbcRepository = taskJdbcRepository;
    }

    @Override
    public List<TaskSummaryResponse> summary() {
        return taskSummaryRepository.findAllByOrderByIdAsc().stream()
                .map(TaskSummaryResponse::from)
                .toList();
    }

    @Override
    public List<DailyTaskCount> tasksPerDay() {
        return taskJdbcRepository.countPerDay();
    }
}
