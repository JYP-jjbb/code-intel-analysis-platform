package com.course.ideology.api.dto;

import com.course.ideology.task.TaskRecord;
import com.course.ideology.task.TaskStatus;
import com.course.ideology.task.TaskType;

import java.time.Instant;

public class TaskDetailResponse {
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

    public static TaskDetailResponse from(TaskRecord record) {
        TaskDetailResponse response = new TaskDetailResponse();
        response.taskId = record.getTaskId();
        response.taskType = record.getTaskType();
        response.status = record.getStatus();
        response.createdAt = record.getCreatedAt();
        response.updatedAt = record.getUpdatedAt();
        response.sourcePath = record.getSourcePath();
        response.resultPath = record.getResultPath();
        response.logPath = record.getLogPath();
        response.message = record.getMessage();
        response.llmModel = record.getLlmModel();
        response.llmConfig = record.getLlmConfig();
        return response;
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
}

