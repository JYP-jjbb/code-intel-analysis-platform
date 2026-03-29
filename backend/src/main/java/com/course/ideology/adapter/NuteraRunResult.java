package com.course.ideology.adapter;

public class NuteraRunResult {
    private final boolean success;
    private final String summary;
    private final String candidateFunctions;
    private final String checkerFeedback;
    private final String artifactSummary;
    private final String message;

    public NuteraRunResult(boolean success, String summary, String candidateFunctions, String checkerFeedback, String artifactSummary, String message) {
        this.success = success;
        this.summary = summary;
        this.candidateFunctions = candidateFunctions;
        this.checkerFeedback = checkerFeedback;
        this.artifactSummary = artifactSummary;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSummary() {
        return summary;
    }

    public String getCandidateFunctions() {
        return candidateFunctions;
    }

    public String getCheckerFeedback() {
        return checkerFeedback;
    }

    public String getArtifactSummary() {
        return artifactSummary;
    }

    public String getMessage() {
        return message;
    }
}
