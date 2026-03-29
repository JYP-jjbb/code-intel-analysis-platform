package com.course.ideology.api.dto;

public class NuteraCaseSourceResponse {
    private String status;
    private String message;
    private String dataset;
    private String csvPath;
    private int entryIndex;
    private int totalEntries;
    private String programClass;
    private String programFunction;
    private String programBinaryPath;
    private String programPath;
    private String code;
    private String log;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

    public String getCsvPath() {
        return csvPath;
    }

    public void setCsvPath(String csvPath) {
        this.csvPath = csvPath;
    }

    public int getEntryIndex() {
        return entryIndex;
    }

    public void setEntryIndex(int entryIndex) {
        this.entryIndex = entryIndex;
    }

    public int getTotalEntries() {
        return totalEntries;
    }

    public void setTotalEntries(int totalEntries) {
        this.totalEntries = totalEntries;
    }

    public String getProgramClass() {
        return programClass;
    }

    public void setProgramClass(String programClass) {
        this.programClass = programClass;
    }

    public String getProgramFunction() {
        return programFunction;
    }

    public void setProgramFunction(String programFunction) {
        this.programFunction = programFunction;
    }

    public String getProgramBinaryPath() {
        return programBinaryPath;
    }

    public void setProgramBinaryPath(String programBinaryPath) {
        this.programBinaryPath = programBinaryPath;
    }

    public String getProgramPath() {
        return programPath;
    }

    public void setProgramPath(String programPath) {
        this.programPath = programPath;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }
}
