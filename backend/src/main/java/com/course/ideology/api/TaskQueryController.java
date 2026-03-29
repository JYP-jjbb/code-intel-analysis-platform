package com.course.ideology.api;

import com.course.ideology.api.dto.CodeReviewTaskResultResponse;
import com.course.ideology.api.dto.NuteraTaskResultResponse;
import com.course.ideology.api.dto.TaskDetailResponse;
import com.course.ideology.api.dto.TaskListResponse;
import com.course.ideology.api.dto.TaskLogResponse;
import com.course.ideology.api.dto.TaskSummaryResponse;
import com.course.ideology.task.TaskRecord;
import com.course.ideology.task.TaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tasks")
public class TaskQueryController {
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public TaskQueryController(TaskService taskService, ObjectMapper objectMapper) {
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public TaskListResponse listTasks(@RequestParam(defaultValue = "20") int limit) {
        List<TaskSummaryResponse> items = taskService.listTasks(limit).stream()
                .map(record -> new TaskSummaryResponse(
                        record.getTaskId(),
                        record.getTaskType(),
                        record.getStatus(),
                        record.getCreatedAt(),
                        record.getUpdatedAt(),
                        record.getMessage(),
                        record.getLlmModel(),
                        record.getLlmConfig()))
                .collect(Collectors.toList());
        return new TaskListResponse(items);
    }

    @GetMapping("/{taskId}")
    public TaskDetailResponse getTask(@PathVariable String taskId) {
        TaskRecord record = taskService.findTask(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        return TaskDetailResponse.from(record);
    }

    @GetMapping("/{taskId}/logs")
    public TaskLogResponse getLogs(@PathVariable String taskId) {
        TaskRecord record = taskService.findTask(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        try {
            Path logPath = Path.of(record.getLogPath());
            if (Files.exists(logPath)) {
                String content = Files.readString(logPath, StandardCharsets.UTF_8);
                return new TaskLogResponse(taskId, content);
            }
        } catch (Exception ignored) {
        }
        return new TaskLogResponse(taskId, "");
    }

    @GetMapping("/{taskId}/result")
    public NuteraTaskResultResponse getResult(@PathVariable String taskId) {
        TaskRecord record = taskService.findTask(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        try {
            Path resultPath = Path.of(record.getResultPath());
            if (Files.exists(resultPath)) {
                return objectMapper.readValue(resultPath.toFile(), NuteraTaskResultResponse.class);
            }
        } catch (Exception ignored) {
        }
        return new NuteraTaskResultResponse("", "", "", "", "");
    }

    @GetMapping("/{taskId}/code-review-result")
    public CodeReviewTaskResultResponse getCodeReviewResult(@PathVariable String taskId) {
        TaskRecord record = taskService.findTask(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        try {
            Path resultPath = Path.of(record.getResultPath());
            if (Files.exists(resultPath)) {
                return objectMapper.readValue(resultPath.toFile(), CodeReviewTaskResultResponse.class);
            }
        } catch (Exception ignored) {
        }
        return new CodeReviewTaskResultResponse("", "", "", "", "");
    }
}
