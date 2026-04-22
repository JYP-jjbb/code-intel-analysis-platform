package com.course.ideology.api.dto;

import com.course.ideology.task.TaskRecord;

import java.time.Instant;

public class CodeRunTaskDetailResponse {
    private String taskId;
    private String language;
    private String taskStatus;
    private String compileStatus;
    private String runStatus;
    private String stdout;
    private String stderr;
    private Integer exitCode;
    private Long timeMs;
    private String errorMessage;
    private String message;
    private Instant createdAt;
    private Instant startTime;
    private Instant finishTime;

    public static CodeRunTaskDetailResponse pending(TaskRecord record, String language) {
        CodeRunTaskDetailResponse response = new CodeRunTaskDetailResponse();
        response.setTaskId(record.getTaskId());
        response.setLanguage(language == null ? "" : language);
        response.setTaskStatus(record.getStatus().name());
        response.setCompileStatus("PENDING");
        response.setRunStatus("PENDING");
        response.setStdout("");
        response.setStderr("");
        response.setExitCode(null);
        response.setTimeMs(0L);
        response.setErrorMessage("");
        response.setMessage(record.getMessage() == null ? "" : record.getMessage());
        response.setCreatedAt(record.getCreatedAt());
        return response;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public String getCompileStatus() {
        return compileStatus;
    }

    public void setCompileStatus(String compileStatus) {
        this.compileStatus = compileStatus;
    }

    public String getRunStatus() {
        return runStatus;
    }

    public void setRunStatus(String runStatus) {
        this.runStatus = runStatus;
    }

    public String getStdout() {
        return stdout;
    }

    public void setStdout(String stdout) {
        this.stdout = stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public void setStderr(String stderr) {
        this.stderr = stderr;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public void setExitCode(Integer exitCode) {
        this.exitCode = exitCode;
    }

    public Long getTimeMs() {
        return timeMs;
    }

    public void setTimeMs(Long timeMs) {
        this.timeMs = timeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(Instant finishTime) {
        this.finishTime = finishTime;
    }
}
