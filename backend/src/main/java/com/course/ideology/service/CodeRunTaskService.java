package com.course.ideology.service;

import com.course.ideology.api.dto.CodeRunTaskDetailResponse;
import com.course.ideology.api.dto.CodeRunTaskRequest;
import com.course.ideology.executor.CodeRunExecutor;
import com.course.ideology.storage.WorkspaceManager;
import com.course.ideology.task.TaskRecord;
import com.course.ideology.task.TaskRepository;
import com.course.ideology.task.TaskStatus;
import com.course.ideology.task.TaskType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class CodeRunTaskService {
    private final TaskRepository taskRepository;
    private final WorkspaceManager workspaceManager;
    private final ObjectMapper objectMapper;
    private final CodeRunExecutor codeRunExecutor;
    private final int maxSourceChars;
    private final int maxStdinChars;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public CodeRunTaskService(TaskRepository taskRepository,
                              WorkspaceManager workspaceManager,
                              ObjectMapper objectMapper,
                              CodeRunExecutor codeRunExecutor,
                              @Value("${app.code-run.max-source-chars:120000}") int maxSourceChars,
                              @Value("${app.code-run.max-stdin-chars:60000}") int maxStdinChars) {
        this.taskRepository = taskRepository;
        this.workspaceManager = workspaceManager;
        this.objectMapper = objectMapper;
        this.codeRunExecutor = codeRunExecutor;
        this.maxSourceChars = Math.max(2000, maxSourceChars);
        this.maxStdinChars = Math.max(2000, maxStdinChars);
    }

    public TaskRecord submit(CodeRunTaskRequest request) {
        String language = normalizeLanguage(request.getLanguage());
        String sourceCode = String.valueOf(request.getSourceCode() == null ? "" : request.getSourceCode());
        String stdin = String.valueOf(request.getStdin() == null ? "" : request.getStdin());

        if (sourceCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代码不能为空");
        }
        if (sourceCode.length() > maxSourceChars) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "代码长度超过限制，当前上限为 " + maxSourceChars + " 字符");
        }
        if (stdin.length() > maxStdinChars) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stdin 长度超过限制，当前上限为 " + maxStdinChars + " 字符");
        }

        request.setLanguage(language);
        request.setSourceCode(sourceCode);
        request.setStdin(stdin);

        String taskId = UUID.randomUUID().toString();
        TaskRecord record = new TaskRecord(taskId, TaskType.CODE_RUN);
        Path taskDir = workspaceManager.createTaskDir(taskId);
        Path sourcePath = taskDir.resolve("source.json");
        Path logPath = taskDir.resolve("run.log");
        Path resultPath = taskDir.resolve("result.json");

        record.setSourcePath(sourcePath.toString());
        record.setLogPath(logPath.toString());
        record.setResultPath(resultPath.toString());
        record.updateStatus(TaskStatus.PENDING, "Queued");
        taskRepository.save(record);

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", taskId);
            payload.put("language", language);
            payload.put("sourceCode", sourceCode);
            payload.put("stdin", stdin);
            payload.put("createdAt", Instant.now().toString());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(sourcePath.toFile(), payload);

            CodeRunTaskDetailResponse pending = CodeRunTaskDetailResponse.pending(record, language);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(resultPath.toFile(), pending);
        } catch (Exception ex) {
            record.updateStatus(TaskStatus.FAILED, "Failed to create code run task payload");
            taskRepository.save(record);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "任务初始化失败: " + ex.getMessage(), ex);
        }

        record.updateStatus(TaskStatus.RUNNING, "Queued");
        taskRepository.save(record);
        executorService.submit(() -> codeRunExecutor.execute(record, request));
        return record;
    }

    public CodeRunTaskDetailResponse query(String taskId) {
        TaskRecord record = findTask(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + taskId));
        try {
            Path resultPath = Path.of(record.getResultPath());
            if (Files.exists(resultPath)) {
                CodeRunTaskDetailResponse detail = objectMapper.readValue(resultPath.toFile(), CodeRunTaskDetailResponse.class);
                detail.setTaskId(record.getTaskId());
                if (detail.getTaskStatus() == null || detail.getTaskStatus().isBlank()) {
                    detail.setTaskStatus(record.getStatus().name());
                }
                if (detail.getCreatedAt() == null) {
                    detail.setCreatedAt(record.getCreatedAt());
                }
                if (detail.getMessage() == null || detail.getMessage().isBlank()) {
                    detail.setMessage(record.getMessage());
                }
                return detail;
            }
        } catch (Exception ignored) {
            // fall through to pending view
        }
        String language = readLanguageFromSource(record);
        return CodeRunTaskDetailResponse.pending(record, language);
    }

    private Optional<TaskRecord> findTask(String taskId) {
        return taskRepository.findById(taskId);
    }

    private String readLanguageFromSource(TaskRecord record) {
        try {
            Path sourcePath = Path.of(record.getSourcePath());
            if (!Files.exists(sourcePath)) {
                return "";
            }
            String content = Files.readString(sourcePath, StandardCharsets.UTF_8);
            Map<?, ?> source = objectMapper.readValue(content, Map.class);
            Object language = source.get("language");
            return language == null ? "" : String.valueOf(language);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeLanguage(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if ("c++".equals(normalized)) {
            normalized = "cpp";
        }
        switch (normalized) {
            case "cpp":
            case "c":
            case "go":
            case "java":
            case "python":
                return normalized;
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "仅支持 C++ / Java / Python 单文件运行，当前语言为: " + (value == null ? "" : value));
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        executorService.shutdownNow();
    }
}
