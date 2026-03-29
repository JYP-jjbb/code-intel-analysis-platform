package com.course.ideology.executor;

import com.course.ideology.adapter.NuteraAdapter;
import com.course.ideology.adapter.NuteraRunRequest;
import com.course.ideology.adapter.NuteraRunResult;
import com.course.ideology.api.dto.NuteraTaskRequest;
import com.course.ideology.storage.WorkspaceManager;
import com.course.ideology.task.TaskRecord;
import com.course.ideology.task.TaskRepository;
import com.course.ideology.task.TaskStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class NuteraExecutor implements TaskExecutor {
    private final TaskRepository taskRepository;
    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper;
    private final NuteraAdapter nuteraAdapter;

    public NuteraExecutor(TaskRepository taskRepository, WorkspaceManager workspaceManager, ObjectMapper objectMapper, NuteraAdapter nuteraAdapter) {
        this.taskRepository = taskRepository;
        this.workspaceManager = workspaceManager;
        this.objectMapper = objectMapper;
        this.nuteraAdapter = nuteraAdapter;
    }

    @Override
    public void execute(TaskRecord record, NuteraTaskRequest request) {
        Path logPath = workspaceManager.resolveTaskFile(record.getTaskId(), "run.log");
        record.updateStatus(TaskStatus.RUNNING, "Running");
        taskRepository.save(record);

        try {
            appendLog(logPath, "[" + Instant.now() + "] NuTera task started.");
            appendLog(logPath, "[" + Instant.now() + "] Run mode: " + request.getRunMode());
            appendLog(logPath, "[" + Instant.now() + "] Model: " + (request.getModel() == null ? "" : request.getModel()));
            appendLog(logPath, "[" + Instant.now() + "] Parameters: " + (request.getParameters() == null ? "" : request.getParameters()));
            appendLog(logPath, "[" + Instant.now() + "] Benchmark: " + request.getBenchmark());

            Path taskDir = workspaceManager.createTaskDir(record.getTaskId());
            NuteraRunRequest runRequest = new NuteraRunRequest(
                    record.getTaskId(),
                    request.getRunMode(),
                    request.getBenchmark(),
                    request.getParameters(),
                    request.getRankExpression(),
                    taskDir
            );

            NuteraRunResult runResult = nuteraAdapter.run(runRequest, line -> appendLog(logPath, line));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("summary", runResult.getSummary());
            result.put("candidateFunctions", runResult.getCandidateFunctions());
            result.put("checkerFeedback", runResult.getCheckerFeedback());
            result.put("artifactSummary", runResult.getArtifactSummary());
            result.put("message", runResult.getMessage());

            Path resultPath = workspaceManager.resolveTaskFile(record.getTaskId(), "result.json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(resultPath.toFile(), result);

            if (runResult.isSuccess()) {
                record.updateStatus(TaskStatus.SUCCESS, runResult.getMessage());
            } else {
                record.updateStatus(TaskStatus.FAILED, runResult.getMessage());
            }
            taskRepository.save(record);
        } catch (Exception ex) {
            record.updateStatus(TaskStatus.FAILED, ex.getMessage());
            taskRepository.save(record);
        }
    }

    private void appendLog(Path logPath, String message) {
        try {
            Files.writeString(logPath, message + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
