package com.course.ideology.api.dto;

import javax.validation.constraints.NotBlank;

public class NuteraVerificationSummaryRequest {
    @NotBlank
    private String code;

    private String language;
    private String candidateFunction;
    private String checkerStatus;
    private String checkerVerdict;
    private String checkerConclusion;
    private String checkerMessage;
    private String checkerCounterexample;
    private Integer selectedLine;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCandidateFunction() {
        return candidateFunction;
    }

    public void setCandidateFunction(String candidateFunction) {
        this.candidateFunction = candidateFunction;
    }

    public String getCheckerStatus() {
        return checkerStatus;
    }

    public void setCheckerStatus(String checkerStatus) {
        this.checkerStatus = checkerStatus;
    }

    public String getCheckerVerdict() {
        return checkerVerdict;
    }

    public void setCheckerVerdict(String checkerVerdict) {
        this.checkerVerdict = checkerVerdict;
    }

    public String getCheckerConclusion() {
        return checkerConclusion;
    }

    public void setCheckerConclusion(String checkerConclusion) {
        this.checkerConclusion = checkerConclusion;
    }

    public String getCheckerMessage() {
        return checkerMessage;
    }

    public void setCheckerMessage(String checkerMessage) {
        this.checkerMessage = checkerMessage;
    }

    public String getCheckerCounterexample() {
        return checkerCounterexample;
    }

    public void setCheckerCounterexample(String checkerCounterexample) {
        this.checkerCounterexample = checkerCounterexample;
    }

    public Integer getSelectedLine() {
        return selectedLine;
    }

    public void setSelectedLine(Integer selectedLine) {
        this.selectedLine = selectedLine;
    }
}

