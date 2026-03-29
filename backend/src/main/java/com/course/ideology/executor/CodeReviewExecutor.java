package com.course.ideology.executor;

import com.course.ideology.api.dto.CodeReviewTaskRequest;
import com.course.ideology.api.dto.CodeReviewTaskResultResponse;
import com.course.ideology.storage.WorkspaceManager;
import com.course.ideology.task.TaskRecord;
import com.course.ideology.task.TaskRepository;
import com.course.ideology.task.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

@Component
public class CodeReviewExecutor {
    private final TaskRepository taskRepository;
    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper;

    public CodeReviewExecutor(TaskRepository taskRepository, WorkspaceManager workspaceManager, ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.workspaceManager = workspaceManager;
        this.objectMapper = objectMapper;
    }

    public void execute(TaskRecord record, CodeReviewTaskRequest request) {
        try {
            Path logPath = workspaceManager.resolveTaskFile(record.getTaskId(), "run.log");
            Files.writeString(logPath, "[" + Instant.now() + "] Code review task started.\n", StandardCharsets.UTF_8);
            Files.writeString(logPath, "[" + Instant.now() + "] Model: " + safe(request.getModel()) + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(logPath, "[" + Instant.now() + "] Parameters: " + safe(request.getParameters()) + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(logPath, "[" + Instant.now() + "] Repo: " + safe(request.getRepoUrl()) + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
            Files.writeString(logPath, "[" + Instant.now() + "] Zip: " + safe(request.getZipFileName()) + "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

            Thread.sleep(600);

            CodeReviewTaskResultResponse result = new CodeReviewTaskResultResponse(
                    "- backend/src/main/java/...\n- frontend/src/pages/...\n- docs/README.md",
                    "1. Missing unit tests for core services\n2. Possible null handling gaps in input parsing",
                    "MEDIUM",
                    "- Add validation for empty inputs\n- Introduce basic unit tests for TaskService",
                    "Mock summary: review completed with medium risk."
            );

            Path resultPath = workspaceManager.resolveTaskFile(record.getTaskId(), "result.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(resultPath.toFile(), result);

            record.updateStatus(TaskStatus.SUCCESS, "Completed");
            taskRepository.save(record);
        } catch (Exception ex) {
            record.updateStatus(TaskStatus.FAILED, ex.getMessage());
            taskRepository.save(record);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

