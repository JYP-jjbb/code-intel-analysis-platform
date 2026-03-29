package com.course.ideology.api.dto;

public class NuteraTaskResultResponse {
    private String summary;
    private String candidateFunctions;
    private String checkerFeedback;
    private String artifactSummary;
    private String message;

    public NuteraTaskResultResponse() {
    }

    public NuteraTaskResultResponse(String summary, String candidateFunctions, String checkerFeedback, String artifactSummary) {
        this.summary = summary;
        this.candidateFunctions = candidateFunctions;
        this.checkerFeedback = checkerFeedback;
        this.artifactSummary = artifactSummary;
    }

    public NuteraTaskResultResponse(String summary, String candidateFunctions, String checkerFeedback, String artifactSummary, String message) {
        this.summary = summary;
        this.candidateFunctions = candidateFunctions;
        this.checkerFeedback = checkerFeedback;
        this.artifactSummary = artifactSummary;
        this.message = message;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getCandidateFunctions() {
        return candidateFunctions;
    }

    public void setCandidateFunctions(String candidateFunctions) {
        this.candidateFunctions = candidateFunctions;
    }

    public String getCheckerFeedback() {
        return checkerFeedback;
    }

    public void setCheckerFeedback(String checkerFeedback) {
        this.checkerFeedback = checkerFeedback;
    }

    public String getArtifactSummary() {
        return artifactSummary;
    }

    public void setArtifactSummary(String artifactSummary) {
        this.artifactSummary = artifactSummary;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
