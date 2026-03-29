package com.course.ideology.adapter;

import java.nio.file.Path;

public class NuteraRunRequest {
    private final String taskId;
    private final String runMode;
    private final String benchmark;
    private final String parameters;
    private final String rankExpression;
    private final Path taskWorkspace;

    public NuteraRunRequest(String taskId, String runMode, String benchmark, String parameters, String rankExpression, Path taskWorkspace) {
        this.taskId = taskId;
        this.runMode = runMode;
        this.benchmark = benchmark;
        this.parameters = parameters;
        this.rankExpression = rankExpression;
        this.taskWorkspace = taskWorkspace;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getRunMode() {
        return runMode;
    }

    public String getBenchmark() {
        return benchmark;
    }

    public String getParameters() {
        return parameters;
    }

    public String getRankExpression() {
        return rankExpression;
    }

    public Path getTaskWorkspace() {
        return taskWorkspace;
    }
}
