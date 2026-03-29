package com.course.ideology.task;

import com.course.ideology.api.dto.CodeReviewTaskRequest;
import com.course.ideology.api.dto.NuteraTaskRequest;
import com.course.ideology.executor.CodeReviewExecutor;
import com.course.ideology.executor.NuteraExecutor;
import com.course.ideology.storage.WorkspaceManager;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaskService {
    private static final Pattern MODEL_PARAM_PATTERN = Pattern.compile("(?:^|\\s)--model\\s+([^\\s]+)");

    private final TaskRepository taskRepository;
    private final WorkspaceManager workspaceManager;
    private final NuteraExecutor nuteraExecutor;
    private final CodeReviewExecutor codeReviewExecutor;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public TaskService(TaskRepository taskRepository, WorkspaceManager workspaceManager, NuteraExecutor nuteraExecutor, CodeReviewExecutor codeReviewExecutor) {
        this.taskRepository = taskRepository;
        this.workspaceManager = workspaceManager;
        this.nuteraExecutor = nuteraExecutor;
        this.codeReviewExecutor = codeReviewExecutor;
    }

    public TaskRecord submitNuteraTask(NuteraTaskRequest request) {
        if (request.getRunMode() == null || request.getRunMode().isBlank()) {
            request.setRunMode("run");
        }
        if (request.getBenchmark() == null || request.getBenchmark().isBlank()) {
            request.setBenchmark("nuTerm_advantage_set.csv");
        }
        String taskId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord(taskId, TaskType.NUTERA);
        String llmModel = resolveModel(request.getModel(), request.getParameters());
        record.setLlmModel(llmModel);
        record.setLlmConfig(composeLlmConfig(llmModel, request.getParameters()));

        Path taskDir = workspaceManager.createTaskDir(taskId);
        Path sourcePath = taskDir.resolve("input.txt");
        Path logPath = taskDir.resolve("run.log");
        Path resultPath = taskDir.resolve("result.json");

        record.setSourcePath(sourcePath.toString());
        record.setLogPath(logPath.toString());
        record.setResultPath(resultPath.toString());
        record.updateStatus(TaskStatus.RUNNING, "Queued");

        boolean inputWritten = true;
        try {
            Files.writeString(sourcePath, request.getCode(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            record.updateStatus(TaskStatus.FAILED, "Failed to write input");
            inputWritten = false;
        }

        taskRepository.save(record);
        if (inputWritten) {
            executorService.submit(() -> nuteraExecutor.execute(record, request));
        }
        return record;
    }

    public Optional<TaskRecord> findTask(String taskId) {
        return taskRepository.findById(taskId);
    }

    public List<TaskRecord> listTasks(int limit) {
        List<TaskRecord> all = taskRepository.findAll();
        if (limit <= 0 || all.size() <= limit) {
            return all;
        }
        return all.subList(0, limit);
    }

    public TaskRecord submitCodeReviewTask(CodeReviewTaskRequest request) {
        String taskId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord(taskId, TaskType.CODE_REVIEW);
        String llmModel = resolveModel(request.getModel(), request.getParameters());
        record.setLlmModel(llmModel);
        record.setLlmConfig(composeLlmConfig(llmModel, request.getParameters()));

        Path taskDir = workspaceManager.createTaskDir(taskId);
        Path sourcePath = taskDir.resolve("source.json");
        Path logPath = taskDir.resolve("run.log");
        Path resultPath = taskDir.resolve("result.json");

        record.setSourcePath(sourcePath.toString());
        record.setLogPath(logPath.toString());
        record.setResultPath(resultPath.toString());
        record.updateStatus(TaskStatus.RUNNING, "Queued");

        try {
            String payload = "{\n" +
                    "  \"repoUrl\": \"" + safe(request.getRepoUrl()) + "\",\n" +
                    "  \"zipFileName\": \"" + safe(request.getZipFileName()) + "\",\n" +
                    "  \"localFolder\": \"" + safe(request.getLocalFolder()) + "\",\n" +
                    "  \"parameters\": \"" + safe(request.getParameters()) + "\"\n" +
                    "}\n";
            Files.writeString(sourcePath, payload, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            record.updateStatus(TaskStatus.FAILED, "Failed to write input");
        }

        taskRepository.save(record);
        if (record.getStatus() == TaskStatus.RUNNING) {
            executorService.submit(() -> codeReviewExecutor.execute(record, request));
        }
        return record;
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private String extractModelFromParameters(String parameters) {
        String text = parameters == null ? "" : parameters.trim();
        if (text.isBlank()) {
            return "";
        }
        Matcher matcher = MODEL_PARAM_PATTERN.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        String model = matcher.group(1);
        if (model == null) {
            return "";
        }
        return model.replaceAll("^[\"'`]+|[\"'`]+$", "").trim();
    }

    private String resolveModel(String directModel, String parameters) {
        String explicit = directModel == null ? "" : directModel.trim();
        if (!explicit.isBlank()) {
            return explicit;
        }
        return extractModelFromParameters(parameters);
    }

    private String composeLlmConfig(String model, String parameters) {
        String normalizedModel = model == null ? "" : model.trim();
        String normalizedParams = parameters == null ? "" : parameters.trim();
        if (!normalizedModel.isBlank() && !normalizedParams.isBlank()) {
            return "model=" + normalizedModel + "; parameters=" + normalizedParams;
        }
        if (!normalizedModel.isBlank()) {
            return "model=" + normalizedModel;
        }
        return normalizedParams;
    }
}
