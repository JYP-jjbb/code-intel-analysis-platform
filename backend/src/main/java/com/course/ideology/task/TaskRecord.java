package com.course.ideology.task;

import java.time.Instant;

public class TaskRecord {
    private String taskId;
    private TaskType taskType;
    private TaskStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private String sourcePath;
    private String resultPath;
    private String logPath;
    private String message;
    private String llmModel;
    private String llmConfig;

    public TaskRecord(String taskId, TaskType taskType) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.status = TaskStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getResultPath() {
        return resultPath;
    }

    public String getLogPath() {
        return logPath;
    }

    public String getMessage() {
        return message;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public String getLlmConfig() {
        return llmConfig;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public void setResultPath(String resultPath) {
        this.resultPath = resultPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public void setLlmConfig(String llmConfig) {
        this.llmConfig = llmConfig;
    }

    public void updateStatus(TaskStatus status, String message) {
        this.status = status;
        this.message = message;
        this.updatedAt = Instant.now();
    }
}

