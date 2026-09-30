package dev.springbootdocs.examples.tasks.service;

import dev.springbootdocs.examples.tasks.dto.DailyTaskCount;
import dev.springbootdocs.examples.tasks.dto.TaskSummaryResponse;
import java.util.List;

public interface TaskReportService {

    List<TaskSummaryResponse> summary();

    List<DailyTaskCount> tasksPerDay();
}
