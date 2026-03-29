package com.course.ideology.service;

import com.course.ideology.api.dto.NuteraBatchStartResponse;
import com.course.ideology.api.dto.NuteraBatchStatusResponse;
import com.course.ideology.api.dto.NuteraGenerateRequest;
import com.course.ideology.api.dto.NuteraGenerateResponse;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class NuteraBatchTaskService {
    private final NuteraLlmService nuteraLlmService;
    private final NuteraBatchReportStoreService reportStoreService;
    private final Map<String, BatchTaskState> tasks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    public NuteraBatchTaskService(NuteraLlmService nuteraLlmService,
                                  NuteraBatchReportStoreService reportStoreService) {
        this.nuteraLlmService = nuteraLlmService;
        this.reportStoreService = reportStoreService;
    }

    public NuteraBatchStartResponse start(NuteraGenerateRequest request) {
        String taskId = UUID.randomUUID().toString();
        BatchTaskState state = new BatchTaskState(taskId, request.getCaseName(), request.getModel(), buildLlmConfig(request));
        tasks.put(taskId, state);

        NuteraGenerateRequest copiedRequest = copyRequestForBatch(request);
        executorService.submit(() -> runTask(state, copiedRequest));

        NuteraBatchStartResponse response = new NuteraBatchStartResponse();
        response.setTaskId(taskId);
        response.setStatus("RUNNING");
        response.setMessage("Batch task created.");
        return response;
    }

    public NuteraBatchStatusResponse status(String taskId) {
        BatchTaskState state = tasks.get(taskId);
        if (state == null) {
            return null;
        }
        return state.toResponse();
    }

    public PauseResult pause(String taskId) {
        BatchTaskState state = tasks.get(taskId);
        if (state == null) {
            return null;
        }
        state.requestPause();
        return state.toPauseResult();
    }

    private void runTask(BatchTaskState state, NuteraGenerateRequest request) {
        try {
            NuteraGenerateResponse finalResponse = nuteraLlmService.runBatchWithProgress(
                    request,
                    snapshot -> state.updateFromSnapshot(snapshot),
                    state::isPauseRequested
            );
            if ("PAUSED".equalsIgnoreCase(finalResponse.getStatus())) {
                state.pause(finalResponse.getBatchResultPath(), finalResponse.getMessage());
            } else if ("SUCCESS".equalsIgnoreCase(finalResponse.getStatus())) {
                state.complete(finalResponse.getBatchResultPath(), "Batch task completed.");
            } else {
                state.fail(finalResponse.getMessage(), finalResponse.getBatchResultPath());
            }
            reportStoreService.saveReport(state.toResponse());
        } catch (Exception ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank() ? "Batch task failed." : ex.getMessage();
            state.fail(message, "");
            reportStoreService.saveReport(state.toResponse());
        }
    }

    private NuteraGenerateRequest copyRequestForBatch(NuteraGenerateRequest request) {
        NuteraGenerateRequest copied = new NuteraGenerateRequest();
        copied.setCode(request.getCode());
        copied.setModel(request.getModel());
        copied.setLanguage(request.getLanguage());
        copied.setCaseName(request.getCaseName());
        copied.setFileName(request.getFileName());
        copied.setExtraConfig(request.getExtraConfig());
        copied.setBatchMode(true);
        return copied;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private static final class BatchTaskState {
        private final String taskId;
        private final String datasetName;
        private final String llmModel;
        private final String llmConfig;
        private String status;
        private int totalCases;
        private int completedCases;
        private int provedCount;
        private int notProvedCount;
        private int errorCount;
        private int stopCount;
        private String currentCaseName;
        private int currentIndex;
        private int currentAttempt;
        private List<String> currentAttemptStates;
        private List<NuteraGenerateResponse.BatchCaseResult> results;
        private String message;
        private Instant startedAt;
        private Instant finishedAt;
        private String resultPath;
        private boolean pauseRequested;

        private BatchTaskState(String taskId, String datasetName, String llmModel, String llmConfig) {
            this.taskId = taskId;
            this.datasetName = datasetName == null ? "" : datasetName;
            this.llmModel = llmModel == null ? "" : llmModel;
            this.llmConfig = llmConfig == null ? "" : llmConfig;
            this.status = "RUNNING";
            this.totalCases = 0;
            this.completedCases = 0;
            this.provedCount = 0;
            this.notProvedCount = 0;
            this.errorCount = 0;
            this.stopCount = 0;
            this.currentCaseName = "";
            this.currentIndex = 0;
            this.currentAttempt = 0;
            this.currentAttemptStates = new ArrayList<>(List.of("", "", "", "", ""));
            this.results = new ArrayList<>();
            this.message = "Batch task started.";
            this.startedAt = Instant.now();
            this.finishedAt = null;
            this.resultPath = "";
            this.pauseRequested = false;
        }

        private synchronized void updateFromSnapshot(NuteraLlmService.BatchProgressSnapshot snapshot) {
            this.status = snapshot.getStatus();
            if (pauseRequested && "RUNNING".equalsIgnoreCase(this.status)) {
                this.status = "PAUSING";
            }
            this.totalCases = snapshot.getTotalCases();
            this.completedCases = snapshot.getCompletedCases();
            this.provedCount = snapshot.getProvedCount();
            this.notProvedCount = snapshot.getNotProvedCount();
            this.errorCount = snapshot.getErrorCount();
            this.stopCount = snapshot.getStopCount();
            this.currentCaseName = snapshot.getCurrentCaseName();
            this.currentIndex = Math.max(0, snapshot.getCompletedCases());
            this.currentAttempt = snapshot.getCurrentAttempt();
            this.currentAttemptStates = new ArrayList<>(snapshot.getCurrentAttemptStates());
            this.results = new ArrayList<>(snapshot.getResults());
            this.startedAt = snapshot.getStartedAt() == null ? this.startedAt : snapshot.getStartedAt();
            this.finishedAt = snapshot.getFinishedAt();
            this.resultPath = snapshot.getResultPath();
            if (snapshot.getMessage() != null && !snapshot.getMessage().isBlank()) {
                this.message = snapshot.getMessage();
            }
        }

        private synchronized void complete(String resultPath, String message) {
            this.status = "COMPLETED";
            this.currentIndex = this.completedCases;
            this.finishedAt = Instant.now();
            if (resultPath != null && !resultPath.isBlank()) {
                this.resultPath = resultPath;
            }
            if (message != null && !message.isBlank()) {
                this.message = message;
            }
        }

        private synchronized void fail(String message, String resultPath) {
            this.status = "FAILED";
            this.currentIndex = this.completedCases;
            this.finishedAt = Instant.now();
            if (resultPath != null && !resultPath.isBlank()) {
                this.resultPath = resultPath;
            }
            this.message = message == null || message.isBlank() ? "Batch task failed." : message;
        }

        private synchronized void requestPause() {
            if ("RUNNING".equalsIgnoreCase(this.status) || "PAUSING".equalsIgnoreCase(this.status)) {
                this.pauseRequested = true;
                this.status = "PAUSING";
                this.message = "暂停请求已提交：将在当前案例完成后暂停后续案例。";
            }
        }

        private synchronized boolean isPauseRequested() {
            return pauseRequested;
        }

        private synchronized void pause(String resultPath, String message) {
            this.status = "PAUSED";
            this.pauseRequested = false;
            this.currentIndex = this.completedCases;
            this.finishedAt = Instant.now();
            if (resultPath != null && !resultPath.isBlank()) {
                this.resultPath = resultPath;
            }
            this.message = message == null || message.isBlank() ? "Batch task paused." : message;
        }

        private synchronized NuteraBatchStatusResponse toResponse() {
            NuteraBatchStatusResponse response = new NuteraBatchStatusResponse();
            response.setTaskId(taskId);
            response.setDatasetName(datasetName);
            response.setLlmModel(llmModel);
            response.setLlmConfig(llmConfig);
            response.setStatus(status);
            response.setTotalCases(totalCases);
            response.setTotalCount(totalCases);
            response.setCompletedCases(completedCases);
            response.setCompletedCount(completedCases);
            response.setProvedCount(provedCount);
            response.setNotProvedCount(notProvedCount);
            response.setErrorCount(errorCount);
            response.setStopCount(stopCount);
            response.setCurrentCaseName(currentCaseName);
            response.setCurrentIndex(currentIndex);
            response.setCurrentAttempt(currentAttempt);
            response.setCurrentAttemptStates(currentAttemptStates);
            response.setResults(results);
            response.setMessage(message);
            response.setStartedAt(startedAt == null ? "" : startedAt.toString());
            response.setFinishedAt(finishedAt == null ? "" : finishedAt.toString());
            response.setResultPath(resultPath);
            return response;
        }

        private synchronized PauseResult toPauseResult() {
            PauseResult result = new PauseResult();
            result.success = true;
            result.taskId = taskId;
            result.status = status;
            result.message = message;
            result.currentIndex = currentIndex;
            return result;
        }
    }

    public static final class PauseResult {
        private boolean success;
        private String taskId;
        private String status;
        private String message;
        private int currentIndex;

        public boolean isSuccess() {
            return success;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public int getCurrentIndex() {
            return currentIndex;
        }
    }

    private String buildLlmConfig(NuteraGenerateRequest request) {
        if (request == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String model = request.getModel() == null ? "" : request.getModel().trim();
        String language = request.getLanguage() == null ? "" : request.getLanguage().trim();
        String extraConfig = request.getExtraConfig() == null ? "" : request.getExtraConfig().trim();
        if (!model.isBlank()) {
            sb.append("model=").append(model);
        }
        if (!language.isBlank()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("language=").append(language);
        }
        if (!extraConfig.isBlank()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append("extraConfig=").append(extraConfig);
        }
        return sb.toString();
    }
}
