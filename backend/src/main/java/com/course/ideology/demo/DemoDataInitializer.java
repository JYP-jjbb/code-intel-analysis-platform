package com.course.ideology.demo;

import com.course.ideology.api.dto.CodeReviewTaskResultResponse;
import com.course.ideology.api.dto.NuteraTaskResultResponse;
import com.course.ideology.storage.WorkspaceManager;
import com.course.ideology.task.TaskRecord;
import com.course.ideology.task.TaskRepository;
import com.course.ideology.task.TaskStatus;
import com.course.ideology.task.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Component
public class DemoDataInitializer {
    private final TaskRepository taskRepository;
    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public DemoDataInitializer(TaskRepository taskRepository,
                               WorkspaceManager workspaceManager,
                               ObjectMapper objectMapper,
                               @Value("${app.demo.enabled:true}") boolean enabled) {
        this.taskRepository = taskRepository;
        this.workspaceManager = workspaceManager;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            return;
        }
        createNuteraDemo();
        createCodeReviewDemo();
    }

    private void createNuteraDemo() {
        String taskId = "demo-nutera-" + UUID.randomUUID().toString().substring(0, 8);
        TaskRecord record = new TaskRecord(taskId, TaskType.NUTERA);
        String model = "deepseek-ai/DeepSeek-V3.2";
        Path taskDir = workspaceManager.createTaskDir(taskId);
        Path logPath = taskDir.resolve("run.log");
        Path resultPath = taskDir.resolve("result.json");
        Path sourcePath = taskDir.resolve("input.txt");

        record.setSourcePath(sourcePath.toString());
        record.setLogPath(logPath.toString());
        record.setResultPath(resultPath.toString());
        record.setLlmModel(model);
        record.setLlmConfig("model=" + model);

        try {
            Files.writeString(sourcePath, "// demo input\nwhile (x > 0) { x = x - 1; }", StandardCharsets.UTF_8);
            Files.writeString(logPath,
                    "[" + Instant.now() + "] Demo NuTera task finished.\n" +
                            "[" + Instant.now() + "] Model: " + model + "\n",
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }

        NuteraTaskResultResponse result = new NuteraTaskResultResponse(
                "Demo summary loaded from NuTera results.",
                "rank(x) = x",
                "checker: YES",
                "final_summary.txt, program_level.csv, attempt_level.csv, checkpoint.pkl"
        );
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(resultPath.toFile(), result);
        } catch (Exception ignored) {
        }

        record.updateStatus(TaskStatus.SUCCESS, "Demo completed");
        taskRepository.save(record);
    }

    private void createCodeReviewDemo() {
        String taskId = "demo-review-" + UUID.randomUUID().toString().substring(0, 8);
        TaskRecord record = new TaskRecord(taskId, TaskType.CODE_REVIEW);
        String model = "Pro/moonshotai/Kimi-K2.5";
        Path taskDir = workspaceManager.createTaskDir(taskId);
        Path logPath = taskDir.resolve("run.log");
        Path resultPath = taskDir.resolve("result.json");
        Path sourcePath = taskDir.resolve("source.json");

        record.setSourcePath(sourcePath.toString());
        record.setLogPath(logPath.toString());
        record.setResultPath(resultPath.toString());
        record.setLlmModel(model);
        record.setLlmConfig("model=" + model + "; parameters=--model " + model);

        try {
            Files.writeString(
                    sourcePath,
                    "{\"repoUrl\":\"https://github.com/example/repo\",\"parameters\":\"--model " + model + "\"}",
                    StandardCharsets.UTF_8
            );
            Files.writeString(logPath,
                    "[" + Instant.now() + "] Demo code review task finished.\n" +
                            "[" + Instant.now() + "] Model: " + model + "\n",
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }

        CodeReviewTaskResultResponse result = new CodeReviewTaskResultResponse(
                "- backend/src/main/java\n- frontend/src/pages\n- docs/README.md",
                "1. Missing unit tests\n2. Potential null handling issue",
                "LOW",
                "- Add tests\n- Add input validation",
                "Demo review completed with low risk."
        );
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(resultPath.toFile(), result);
        } catch (Exception ignored) {
        }

        record.updateStatus(TaskStatus.SUCCESS, "Demo completed");
        taskRepository.save(record);
    }
}
