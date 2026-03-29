package com.course.ideology.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class NuteraBatchStatusResponse {
    private String taskId;
    private String status;
    private String datasetName;
    private String llmModel;
    private String llmConfig;

    @JsonProperty("totalCases")
    private int totalCases;

    @JsonProperty("totalCount")
    private int totalCount;

    @JsonProperty("completedCases")
    private int completedCases;

    @JsonProperty("completedCount")
    private int completedCount;

    @JsonProperty("provedCount")
    private int provedCount;

    @JsonProperty("notProvedCount")
    private int notProvedCount;

    @JsonProperty("errorCount")
    private int errorCount;

    @JsonProperty("stopCount")
    private int stopCount;

    @JsonProperty("currentCaseName")
    private String currentCaseName;

    @JsonProperty("currentIndex")
    private int currentIndex;

    @JsonProperty("currentAttempt")
    private int currentAttempt;

    @JsonProperty("currentAttemptStates")
    private List<String> currentAttemptStates = new ArrayList<>();

    private List<NuteraGenerateResponse.BatchCaseResult> results = new ArrayList<>();
    private String message;
    private String startedAt;
    private String finishedAt;

    @JsonProperty("resultPath")
    private String resultPath;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public String getLlmConfig() {
        return llmConfig;
    }

    public void setLlmConfig(String llmConfig) {
        this.llmConfig = llmConfig;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getTotalCases() {
        return totalCases;
    }

    public void setTotalCases(int totalCases) {
        this.totalCases = totalCases;
        this.totalCount = totalCases;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
        this.totalCases = totalCount;
    }

    public int getCompletedCases() {
        return completedCases;
    }

    public void setCompletedCases(int completedCases) {
        this.completedCases = completedCases;
        this.completedCount = completedCases;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(int completedCount) {
        this.completedCount = completedCount;
        this.completedCases = completedCount;
    }

    public int getProvedCount() {
        return provedCount;
    }

    public void setProvedCount(int provedCount) {
        this.provedCount = provedCount;
    }

    public int getNotProvedCount() {
        return notProvedCount;
    }

    public void setNotProvedCount(int notProvedCount) {
        this.notProvedCount = notProvedCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public int getStopCount() {
        return stopCount;
    }

    public void setStopCount(int stopCount) {
        this.stopCount = stopCount;
    }

    public String getCurrentCaseName() {
        return currentCaseName;
    }

    public void setCurrentCaseName(String currentCaseName) {
        this.currentCaseName = currentCaseName;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(int currentIndex) {
        this.currentIndex = currentIndex;
    }

    public int getCurrentAttempt() {
        return currentAttempt;
    }

    public void setCurrentAttempt(int currentAttempt) {
        this.currentAttempt = currentAttempt;
    }

    public List<String> getCurrentAttemptStates() {
        return currentAttemptStates;
    }

    public void setCurrentAttemptStates(List<String> currentAttemptStates) {
        this.currentAttemptStates = currentAttemptStates == null ? new ArrayList<>() : new ArrayList<>(currentAttemptStates);
    }

    public List<NuteraGenerateResponse.BatchCaseResult> getResults() {
        return results;
    }

    public void setResults(List<NuteraGenerateResponse.BatchCaseResult> results) {
        this.results = results == null ? new ArrayList<>() : new ArrayList<>(results);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getResultPath() {
        return resultPath;
    }

    public void setResultPath(String resultPath) {
        this.resultPath = resultPath;
    }
}
