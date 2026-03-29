package com.course.ideology.api.dto;

import java.util.ArrayList;
import java.util.List;

public class NuteraBatchReportDetailResponse extends NuteraBatchReportSummaryResponse {
    private List<NuteraGenerateResponse.BatchCaseResult> results = new ArrayList<>();
    private String resultPath;

    public List<NuteraGenerateResponse.BatchCaseResult> getResults() {
        return results;
    }

    public void setResults(List<NuteraGenerateResponse.BatchCaseResult> results) {
        this.results = results == null ? new ArrayList<>() : new ArrayList<>(results);
    }

    public String getResultPath() {
        return resultPath;
    }

    public void setResultPath(String resultPath) {
        this.resultPath = resultPath;
    }
}
