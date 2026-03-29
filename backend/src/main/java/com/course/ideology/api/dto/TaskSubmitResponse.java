package com.course.ideology.api.dto;

import com.course.ideology.task.TaskRecord;

public class TaskSubmitResponse {
    private String taskId;
    private String status;

    public TaskSubmitResponse(String taskId, String status) {
        this.taskId = taskId;
        this.status = status;
    }

    public static TaskSubmitResponse from(TaskRecord record) {
        return new TaskSubmitResponse(record.getTaskId(), record.getStatus().name());
    }

    public String getTaskId() {
        return taskId;
    }

    public String getStatus() {
        return status;
    }
}

