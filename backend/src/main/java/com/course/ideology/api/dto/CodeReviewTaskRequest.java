package com.course.ideology.api.dto;

public class CodeReviewTaskRequest {
    private String repoUrl;
    private String zipFileName;
    private String localFolder;
    private String model;
    private String parameters;

    public String getRepoUrl() {
        return repoUrl;
    }

    public void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl;
    }

    public String getZipFileName() {
        return zipFileName;
    }

    public void setZipFileName(String zipFileName) {
        this.zipFileName = zipFileName;
    }

    public String getLocalFolder() {
        return localFolder;
    }

    public void setLocalFolder(String localFolder) {
        this.localFolder = localFolder;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }
}

