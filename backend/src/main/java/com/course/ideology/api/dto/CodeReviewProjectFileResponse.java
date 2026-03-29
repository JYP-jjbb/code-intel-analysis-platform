package com.course.ideology.api.dto;

public class CodeReviewProjectFileResponse {
    private String pageSessionId;
    private String projectId;
    private String filePath;
    private String content;
    private boolean truncated;
    private boolean binary;

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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public boolean isBinary() {
        return binary;
    }

    public void setBinary(boolean binary) {
        this.binary = binary;
    }
}
