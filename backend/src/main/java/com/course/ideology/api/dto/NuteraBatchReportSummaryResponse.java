package com.course.ideology.api.dto;

public class NuteraBatchReportSummaryResponse {
    private String taskId;
    private String datasetName;
    private String llmModel;
    private String llmConfig;
    private int totalCases;
    private int completedCases;
    private int provedCount;
    private int notProvedCount;
    private int errorCount;
    private int stopCount;
    private String startedAt;
    private String finishedAt;
    private String status;

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getDatasetName() { return datasetName; }
    public void setDatasetName(String datasetName) { this.datasetName = datasetName; }
    public String getLlmModel() { return llmModel; }
    public void setLlmModel(String llmModel) { this.llmModel = llmModel; }
    public String getLlmConfig() { return llmConfig; }
    public void setLlmConfig(String llmConfig) { this.llmConfig = llmConfig; }
    public int getTotalCases() { return totalCases; }
    public void setTotalCases(int totalCases) { this.totalCases = totalCases; }
    public int getCompletedCases() { return completedCases; }
    public void setCompletedCases(int completedCases) { this.completedCases = completedCases; }
    public int getProvedCount() { return provedCount; }
    public void setProvedCount(int provedCount) { this.provedCount = provedCount; }
    public int getNotProvedCount() { return notProvedCount; }
    public void setNotProvedCount(int notProvedCount) { this.notProvedCount = notProvedCount; }
    public int getErrorCount() { return errorCount; }
    public void setErrorCount(int errorCount) { this.errorCount = errorCount; }
    public int getStopCount() { return stopCount; }
    public void setStopCount(int stopCount) { this.stopCount = stopCount; }
    public String getStartedAt() { return startedAt; }
    public void setStartedAt(String startedAt) { this.startedAt = startedAt; }
    public String getFinishedAt() { return finishedAt; }
    public void setFinishedAt(String finishedAt) { this.finishedAt = finishedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
