package com.course.ideology.api.dto;

public class CodeReviewProjectDownloadResponse {
    private String pageSessionId;
    private String projectId;
    private String repoUrl;
    private String localPath;
    private String projectStructure;
    private String focusFilePath;
    private String focusFileContent;
    private boolean reused;
    private String message;

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

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getProjectStructure() {
        return projectStructure;
    }

    public void setProjectStructure(String projectStructure) {
        this.projectStructure = projectStructure;
    }

    public String getFocusFilePath() {
        return focusFilePath;
    }

    public void setFocusFilePath(String focusFilePath) {
        this.focusFilePath = focusFilePath;
    }

    public String getFocusFileContent() {
        return focusFileContent;
    }

    public void setFocusFileContent(String focusFileContent) {
        this.focusFileContent = focusFileContent;
    }

    public boolean isReused() {
        return reused;
    }

    public void setReused(boolean reused) {
        this.reused = reused;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
