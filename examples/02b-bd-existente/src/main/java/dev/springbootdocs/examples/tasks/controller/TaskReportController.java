package dev.springbootdocs.examples.tasks.controller;

import dev.springbootdocs.examples.tasks.dto.DailyTaskCount;
import dev.springbootdocs.examples.tasks.dto.TaskSummaryResponse;
import dev.springbootdocs.examples.tasks.service.TaskReportService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskReportController {

    private final TaskReportService taskReportService;

    public TaskReportController(TaskReportService taskReportService) {
        this.taskReportService = taskReportService;
    }

    @GetMapping("/tasks/summary")
    public List<TaskSummaryResponse> summary() {
        return taskReportService.summary();
    }

    @GetMapping("/reports/tasks-per-day")
    public List<DailyTaskCount> tasksPerDay() {
        return taskReportService.tasksPerDay();
    }
}
