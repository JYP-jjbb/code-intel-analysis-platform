package com.course.ideology.api.dto;

import java.util.List;

public class TaskListResponse {
    private List<TaskSummaryResponse> tasks;

    public TaskListResponse(List<TaskSummaryResponse> tasks) {
        this.tasks = tasks;
    }

    public List<TaskSummaryResponse> getTasks() {
        return tasks;
    }
}
