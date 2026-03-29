package com.course.ideology.api.dto;

import javax.validation.constraints.NotBlank;

public class NuteraTaskRequest {
    @NotBlank
    private String code;
    private String model;
    private String benchmark;
    private String parameters;
    private String runMode;
    private String rankExpression;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBenchmark() {
        return benchmark;
    }

    public void setBenchmark(String benchmark) {
        this.benchmark = benchmark;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public String getRunMode() {
        return runMode;
    }

    public void setRunMode(String runMode) {
        this.runMode = runMode;
    }

    public String getRankExpression() {
        return rankExpression;
    }

    public void setRankExpression(String rankExpression) {
        this.rankExpression = rankExpression;
    }
}

