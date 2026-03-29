package com.course.ideology.api.dto;

import javax.validation.constraints.NotBlank;

public class NuteraBatchPauseRequest {
    @NotBlank
    private String taskId;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
}
