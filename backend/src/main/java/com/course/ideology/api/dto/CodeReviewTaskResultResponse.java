package com.course.ideology.api.dto;

public class CodeReviewTaskResultResponse {
    private String projectStructure;
    private String issueList;
    private String riskLevel;
    private String fixSuggestions;
    private String summary;

    public CodeReviewTaskResultResponse() {
    }

    public CodeReviewTaskResultResponse(String projectStructure, String issueList, String riskLevel, String fixSuggestions, String summary) {
        this.projectStructure = projectStructure;
        this.issueList = issueList;
        this.riskLevel = riskLevel;
        this.fixSuggestions = fixSuggestions;
        this.summary = summary;
    }

    public String getProjectStructure() {
        return projectStructure;
    }

    public void setProjectStructure(String projectStructure) {
        this.projectStructure = projectStructure;
    }

    public String getIssueList() {
        return issueList;
    }

    public void setIssueList(String issueList) {
        this.issueList = issueList;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getFixSuggestions() {
        return fixSuggestions;
    }

    public void setFixSuggestions(String fixSuggestions) {
        this.fixSuggestions = fixSuggestions;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}

