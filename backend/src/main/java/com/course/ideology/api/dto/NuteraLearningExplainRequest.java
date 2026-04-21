package com.course.ideology.api.dto;

import javax.validation.constraints.NotBlank;

public class NuteraLearningExplainRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String model;

    @NotBlank
    private String language;

    private String fileName;

    private Integer selectedLine;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Integer getSelectedLine() {
        return selectedLine;
    }

    public void setSelectedLine(Integer selectedLine) {
        this.selectedLine = selectedLine;
    }
}

