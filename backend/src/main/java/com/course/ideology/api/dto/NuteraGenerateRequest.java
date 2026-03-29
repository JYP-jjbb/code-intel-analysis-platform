package com.course.ideology.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;

import javax.validation.constraints.NotBlank;

public class NuteraGenerateRequest {
    @NotBlank
    private String code;

    @NotBlank
    private String model;

    @NotBlank
    private String language;

    @JsonProperty("case")
    private String caseName;

    private String fileName;

    private String extraConfig;

    @JsonProperty("batchMode")
    @JsonAlias({"batch_mode", "batch"})
    private boolean batchMode;

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

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getExtraConfig() {
        return extraConfig;
    }

    public void setExtraConfig(String extraConfig) {
        this.extraConfig = extraConfig;
    }

    public boolean isBatchMode() {
        return batchMode;
    }

    public void setBatchMode(boolean batchMode) {
        this.batchMode = batchMode;
    }
}
