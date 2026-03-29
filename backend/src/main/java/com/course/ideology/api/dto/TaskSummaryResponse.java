package com.course.ideology.api.dto;

import com.course.ideology.task.TaskStatus;
import com.course.ideology.task.TaskType;

import java.time.Instant;

public class TaskSummaryResponse {
    private String taskId;
    private TaskType taskType;
    private TaskStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private String message;
    private String llmModel;
    private String llmConfig;

    public TaskSummaryResponse() {
    }

    public TaskSummaryResponse(String taskId, TaskType taskType, TaskStatus status, Instant createdAt, Instant updatedAt, String message, String llmModel, String llmConfig) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.message = message;
        this.llmModel = llmModel;
        this.llmConfig = llmConfig;
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
