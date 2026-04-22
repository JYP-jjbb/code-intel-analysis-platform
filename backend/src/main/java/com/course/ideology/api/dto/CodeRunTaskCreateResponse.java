package com.course.ideology.api.dto;

public class CodeRunTaskCreateResponse {
    private String taskId;
    private String taskStatus;

    public CodeRunTaskCreateResponse() {
    }

    public CodeRunTaskCreateResponse(String taskId, String taskStatus) {
        this.taskId = taskId;
        this.taskStatus = taskStatus;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }
}
