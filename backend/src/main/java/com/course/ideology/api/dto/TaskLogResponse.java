package com.course.ideology.api.dto;

public class TaskLogResponse {
    private String taskId;
    private String content;

    public TaskLogResponse(String taskId, String content) {
        this.taskId = taskId;
        this.content = content;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getContent() {
        return content;
    }
}

