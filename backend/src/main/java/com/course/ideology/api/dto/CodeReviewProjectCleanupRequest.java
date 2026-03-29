package com.course.ideology.api.dto;

public class CodeReviewProjectCleanupRequest {
    private String pageSessionId;
    private String projectId;

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
}
