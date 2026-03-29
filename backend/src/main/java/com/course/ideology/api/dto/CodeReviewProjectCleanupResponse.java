package com.course.ideology.api.dto;

public class CodeReviewProjectCleanupResponse {
    private boolean success;
    private String pageSessionId;
    private String projectId;
    private String removedPath;
    private String message;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getPageSessionId() {
        return pageSessionId;
    }

    public void setPageSessionId(String pageSessionId) {
        this.pageSessionId = pageSessionId;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getRemovedPath() {
        return removedPath;
    }

    public void setRemovedPath(String removedPath) {
        this.removedPath = removedPath;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
