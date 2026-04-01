package com.course.ideology.service;

import com.course.ideology.api.dto.NuteraCaseSourceResponse;
import com.course.ideology.api.dto.NuteraGenerateRequest;
import com.course.ideology.api.dto.NuteraGenerateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NuteraLlmService {
    private static final int MAX_BATCH_ATTEMPTS = 5;
    private static final Pattern LEADING_CHECKER_TRACE_TAG = Pattern.compile("^\\s*(?:\\[checker-runtime-v\\d+\\]\\s*)+", Pattern.CASE_INSENSITIVE);

    private final SiliconFlowSettingsService settingsService;
    private final NuteraPromptLoaderService promptLoaderService;
    private final SiliconFlowChatService chatService;
    private final NuteraCaseSourceService caseSourceService;
    private final NuteraCheckerService checkerService;
    private final ObjectMapper objectMapper;
    private final Path projectRoot;
    private final Path runtimeRoot;

    public NuteraLlmService(SiliconFlowSettingsService settingsService,
                            NuteraPromptLoaderService promptLoaderService,
                            SiliconFlowChatService chatService,
                            NuteraCaseSourceService caseSourceService,
                            NuteraCheckerService checkerService,
                            ObjectMapper objectMapper,
                            @Value("${app.nutera.runtimePath:runtimes/nutera}") String configuredRuntimePath) {
        this.settingsService = settingsService;
        this.promptLoaderService = promptLoaderService;
        this.chatService = chatService;
        this.caseSourceService = caseSourceService;
        this.checkerService = checkerService;
        this.objectMapper = objectMapper;
        this.projectRoot = resolveProjectRoot();
        this.runtimeRoot = resolveRuntimeRoot(configuredRuntimePath, projectRoot);
    }

    public NuteraGenerateResponse generateRankingFunction(NuteraGenerateRequest request) {
        if (!chatService.hasApiCredentialForModel(request.getModel(), settingsService.getApiKey())) {
            throw new ApiKeyNotConfiguredException(chatService.buildMissingCredentialMessage(request.getModel()));
        }

        if (request != null) {
            String dataset = request.getCaseName() == null ? "" : request.getCaseName();
            // Explicit trace for frontend logs: confirms whether request entered batch branch.
            System.out.println("[Nutera] generateRankingFunction batchMode=" + request.isBatchMode() + ", dataset=" + dataset);
        }

        if (request.isBatchMode()) {
            return generateBatchRankingFunction(request, null, null);
        }
        return generateSingleRankingFunction(request);
    }

    public NuteraGenerateResponse runBatchWithProgress(NuteraGenerateRequest request, BatchProgressListener listener) {
        return runBatchWithProgress(request, listener, null);
    }

    public NuteraGenerateResponse runBatchWithProgress(NuteraGenerateRequest request,
                                                       BatchProgressListener listener,
                                                       BatchPauseSignal pauseSignal) {
        if (!chatService.hasApiCredentialForModel(request.getModel(), settingsService.getApiKey())) {
            throw new ApiKeyNotConfiguredException(chatService.buildMissingCredentialMessage(request.getModel()));
        }
        return generateBatchRankingFunction(request, listener, pauseSignal);
    }

    private NuteraGenerateResponse generateSingleRankingFunction(NuteraGenerateRequest request) {
        List<String> logs = new ArrayList<>();
        append(logs, "Analysis started.");
        append(logs, "Model: " + request.getModel() + ", language: " + request.getLanguage());

        try {
            append(logs, "Loading prompt file.");
            String prompt = promptLoaderService.loadPrompt();
            append(logs, "Prompt loaded.");

            ModelGeneration generation = runModelGeneration(request, prompt, logs);

            NuteraCheckerService.CheckerResult checkerResult;
            NuteraCaseSourceResponse caseSource = null;
            String dataset = nonNull(request.getCaseName()).trim();
            if (dataset.isBlank() || "none".equalsIgnoreCase(dataset)) {
                checkerResult = NuteraCheckerService.CheckerResult.skipped(
                        "No dataset selected. Checker requires dataset program metadata."
                );
                append(logs, "Checker skipped: no dataset selected.");
            } else {
                append(logs, "Loading case metadata for checker: " + dataset);
                caseSource = caseSourceService.loadCaseSource(dataset, 0);
                appendCaseSourceLog(logs, caseSource.getLog(), "[case-source]");
                checkerResult = checkerService.runChecker(generation.rankExpression, caseSource, logs);
            }

            NuteraGenerateResponse response = new NuteraGenerateResponse();
            response.setStatus("SUCCESS");
            response.setCandidateFunction(generation.candidateFunction);
            response.setRawResponse(generation.rawResponse);
            response.setLog(String.join("\n", logs));
            response.setCheckerStatus(checkerResult.getStatus());
            response.setCheckerMessage(checkerResult.getMessage());
            response.setCheckerRawOutput(checkerResult.getRawOutput());
            response.setCheckerDebugMessage(checkerResult.getDebugMessage());
            response.setCheckerTraceTag(checkerResult.getTraceTag());
            response.setCheckerVerdict(normalizeVerificationStatus(checkerResult));
            response.setCheckerConclusion(normalizeConclusion(checkerResult));
            response.setCheckerCounterexample(checkerResult.getCounterexample());
            response.setCheckerFeedback(buildCheckerFeedback(checkerResult));
            response.setFinalSummary(buildFinalSummary(caseSource, checkerResult));
            response.setMessage("Generation succeeded.");
            response.setBatchMode(false);
            response.setBatchResults(List.of());
            return response;
        } catch (Exception ex) {
            String readableMessage = normalizeErrorMessage(ex.getMessage());
            append(logs, "Generation failed: " + readableMessage);

            NuteraGenerateResponse response = new NuteraGenerateResponse();
            response.setStatus("FAILED");
            response.setCandidateFunction("");
            response.setRawResponse("");
            response.setLog(String.join("\n", logs));
            response.setCheckerStatus("CHECKER_ERROR");
            response.setCheckerMessage(readableMessage);
            response.setCheckerRawOutput("");
            response.setCheckerDebugMessage("");
            response.setCheckerTraceTag("");
            response.setCheckerVerdict("CHECKER_ERROR");
            response.setCheckerConclusion("ERROR");
            response.setCheckerCounterexample("");
            response.setCheckerFeedback("Model call failed. Please check runtime logs and Moonshot/DeepSeek settings.");
            response.setFinalSummary("Generation failed. Checker was not executed.");
            response.setMessage(readableMessage);
            response.setBatchMode(false);
            response.setBatchResults(List.of());
            return response;
        }
    }

    private NuteraGenerateResponse generateBatchRankingFunction(NuteraGenerateRequest request,
                                                                BatchProgressListener progressListener,
                                                                BatchPauseSignal pauseSignal) {
        List<String> logs = new ArrayList<>();
        Instant startedAt = Instant.now();
        int total = 0;
        List<NuteraGenerateResponse.BatchCaseResult> batchResults = new ArrayList<>();
        int proved = 0;
        int notProved = 0;
        int stop = 0;
        String currentCase = "";
        int currentAttempt = 0;
        List<String> currentAttemptStates = emptyAttemptStates();
        append(logs, "Batch request confirmed: batchMode=true");
        append(logs, "Batch analysis started.");
        append(logs, "Model: " + request.getModel() + ", language: " + request.getLanguage());

        String dataset = nonNull(request.getCaseName()).trim();
        if (dataset.isBlank() || "none".equalsIgnoreCase(dataset)) {
            String message = "Batch mode requires selecting a dataset.";
            append(logs, message);
            return buildBatchFailedResponse(message, logs);
        }

        try {
            append(logs, "Loading prompt file.");
            String prompt = promptLoaderService.loadPrompt();
            append(logs, "Prompt loaded.");

            append(logs, "Loading dataset metadata: " + dataset);
            NuteraCaseSourceResponse firstCase = caseSourceService.loadCaseSource(dataset, 0);
            appendCaseSourceLog(logs, firstCase.getLog(), "[case-source#1]");

            total = Math.max(1, firstCase.getTotalEntries());
            append(logs, "Batch total entries: " + total);

            publishBatchProgress(progressListener, "RUNNING", total, 0, proved, notProved, 0, stop,
                    currentCase, currentAttempt, currentAttemptStates, batchResults, startedAt, null, "", "Batch task started.");
            if (isPauseRequested(pauseSignal)) {
                return buildBatchPausedResponse(dataset, logs, batchResults, total, proved, notProved, stop,
                        currentCase, currentAttempt, currentAttemptStates, progressListener, startedAt, "暂停请求已生效：当前案例完成后，后续案例已停止。");
            }

            for (int index = 0; index < total; index++) {
                if (isPauseRequested(pauseSignal)) {
                    return buildBatchPausedResponse(dataset, logs, batchResults, total, proved, notProved, stop,
                            currentCase, currentAttempt, currentAttemptStates, progressListener, startedAt, "暂停请求已生效：当前案例完成后，后续案例已停止。");
                }
                List<String> caseLogs = new ArrayList<>();
                NuteraCaseSourceResponse caseSource = null;
                currentCase = "entry-" + (index + 1);
                append(logs, "Batch case " + (index + 1) + "/" + total + " started.");
                try {
                    caseSource = index == 0 ? firstCase : caseSourceService.loadCaseSource(dataset, index);
                    appendCaseSourceLog(logs, caseSource.getLog(), "[case-source#" + (index + 1) + "]");
                    String caseName = buildCaseName(caseSource, index);
                    currentCase = caseName;
                    append(logs, "Processing case: " + caseName);

                    List<String> attemptStates = emptyAttemptStates();
                    RefinementContext refinementContext = null;
                    NuteraGenerateResponse.BatchCaseResult finalCaseResult = null;

                    for (int attempt = 1; attempt <= MAX_BATCH_ATTEMPTS; attempt++) {
                        currentAttempt = attempt;
                        currentAttemptStates = new ArrayList<>(attemptStates);
                        append(caseLogs, "Attempt " + attempt + "/" + MAX_BATCH_ATTEMPTS + " started.");
                        publishBatchProgress(progressListener, "RUNNING", total, batchResults.size(), proved, notProved, 0, stop,
                                currentCase, currentAttempt, currentAttemptStates, batchResults, startedAt, null, "", "Running");

                        NuteraGenerateRequest caseRequest = buildCaseRequest(request, caseSource, caseName);
                        ModelGeneration generation;
                        try {
                            generation = runModelGeneration(caseRequest, prompt, caseLogs, refinementContext);
                        } catch (Exception ex) {
                            String readable = normalizeErrorMessage(ex.getMessage());
                            attemptStates.set(attempt - 1, "NOT_PROVED");
                            append(caseLogs, "Attempt " + attempt + " model generation failed: " + readable);
                            refinementContext = RefinementContext.fromFailure("", "", "", readable, attempt);
                            if (attempt == MAX_BATCH_ATTEMPTS) {
                                finalCaseResult = buildNotProvedCaseResult(caseName, "", "", readable, "",
                                        String.join("\n", caseLogs), attempt, attemptStates, "", "");
                            }
                            continue;
                        }

                        boolean noReluSolution = isNoReluSolutionCandidate(generation.candidateFunction)
                                || isNoReluSolutionCandidate(generation.rankExpression);
                        if (noReluSolution) {
                            attemptStates.set(attempt - 1, "STOP");
                            finalCaseResult = buildStopCaseResult(caseName, generation.candidateFunction, "NO_RELU_SOLUTION",
                                    String.join("\n", caseLogs), attempt, attemptStates);
                            append(caseLogs, "Attempt " + attempt + " returned NO_RELU_SOLUTION.");
                            break;
                        }

                        try {
                            NuteraCheckerService.CheckerResult checkerResult =
                                    checkerService.runChecker(generation.rankExpression, caseSource, caseLogs);
                            String conclusion = normalizeConclusion(checkerResult);
                            if ("YES".equalsIgnoreCase(conclusion)) {
                                attemptStates.set(attempt - 1, "PROVED");
                                finalCaseResult = buildProvedCaseResult(caseName, generation.candidateFunction, checkerResult,
                                        String.join("\n", caseLogs), attempt, attemptStates);
                                break;
                            }

                            attemptStates.set(attempt - 1, "NOT_PROVED");
                            String failureReason = nonNull(checkerResult.getMessage()).isBlank()
                                    ? "Checker could not prove termination."
                                    : checkerResult.getMessage();
                            refinementContext = RefinementContext.fromFailure(
                                    generation.candidateFunction,
                                    buildCheckerFeedback(checkerResult),
                                    checkerResult.getCounterexample(),
                                    failureReason,
                                    attempt
                            );
                            if (attempt == MAX_BATCH_ATTEMPTS) {
                                finalCaseResult = buildNotProvedCaseResult(
                                        caseName,
                                        generation.candidateFunction,
                                        checkerResult.getRawOutput(),
                                        failureReason,
                                        checkerResult.getCounterexample(),
                                        String.join("\n", caseLogs),
                                        attempt,
                                        attemptStates,
                                        checkerResult.getDebugMessage(),
                                        checkerResult.getTraceTag()
                                );
                            }
                        } catch (Exception ex) {
                            String readable = normalizeErrorMessage(ex.getMessage());
                            attemptStates.set(attempt - 1, "NOT_PROVED");
                            refinementContext = RefinementContext.fromFailure(
                                    generation.candidateFunction,
                                    "",
                                    "",
                                    readable,
                                    attempt
                            );
                            append(caseLogs, "Attempt " + attempt + " checker execution failed: " + readable);
                            if (attempt == MAX_BATCH_ATTEMPTS) {
                                finalCaseResult = buildNotProvedCaseResult(caseName, generation.candidateFunction, "",
                                        readable, "", String.join("\n", caseLogs), attempt, attemptStates, "", "");
                            }
                        }
                    }

                    if (finalCaseResult == null) {
                        finalCaseResult = buildNotProvedCaseResult(caseName, "", "",
                                "No valid solution found after attempts.", "", String.join("\n", caseLogs),
                                MAX_BATCH_ATTEMPTS, attemptStates, "", "");
                    }

                    batchResults.add(finalCaseResult);
                    currentAttempt = finalCaseResult.getAttemptCount();
                    currentAttemptStates = new ArrayList<>(finalCaseResult.getAttemptStates());

                    String finalStatus = nonNull(finalCaseResult.getFinalStatus()).toUpperCase(Locale.ROOT);
                    boolean isStopCase = isStopCaseResult(finalCaseResult);
                    if ("PROVED".equals(finalStatus)) {
                        proved++;
                    } else {
                        notProved++;
                    }
                    if (isStopCase) {
                        stop++;
                    }
                    append(logs, "Case completed: " + caseName + ", finalStatus=" + finalStatus + ", attempts=" + finalCaseResult.getAttemptCount());
                    publishBatchProgress(progressListener, "RUNNING", total, batchResults.size(), proved, notProved, 0, stop,
                            caseName, currentAttempt, currentAttemptStates, batchResults, startedAt, null, "", "Running");
                } catch (Exception ex) {
                    String caseName = caseSource == null ? "entry-" + (index + 1) : buildCaseName(caseSource, index);
                    String readable = normalizeErrorMessage(ex.getMessage());
                    append(caseLogs, "Case failed: " + readable);
                    append(logs, "Case failed: " + caseName + ", reason: " + readable);
                    List<String> attemptStates = emptyAttemptStates();
                    attemptStates.set(0, "NOT_PROVED");
                    batchResults.add(buildNotProvedCaseResult(caseName, "", "", readable, "",
                            String.join("\n", caseLogs), 1, attemptStates, "", ""));
                    notProved++;
                    currentAttempt = 1;
                    currentAttemptStates = new ArrayList<>(attemptStates);
                    publishBatchProgress(progressListener, "RUNNING", total, batchResults.size(), proved, notProved, 0, stop,
                            caseName, currentAttempt, currentAttemptStates, batchResults, startedAt, null, "", readable);
                }
            }

            String resultPath = persistBatchResults(dataset, batchResults, logs, total, proved, notProved, stop);
            Instant finishedAt = Instant.now();
            publishBatchProgress(progressListener, "COMPLETED", total, batchResults.size(), proved, notProved, 0, stop,
                    currentCase, currentAttempt, currentAttemptStates, batchResults, startedAt, finishedAt, resultPath, "Batch completed.");

            NuteraGenerateResponse response = new NuteraGenerateResponse();
            response.setStatus("SUCCESS");
            response.setCandidateFunction("");
            response.setRawResponse("");
            response.setLog(String.join("\n", logs));
            response.setCheckerStatus("COMPLETED");
            response.setCheckerMessage("Checker completed for batch.");
            response.setCheckerRawOutput("");
            response.setCheckerDebugMessage("");
            response.setCheckerTraceTag("");
            response.setCheckerVerdict("");
            response.setCheckerConclusion("");
            response.setCheckerCounterexample("");
            response.setCheckerFeedback("Batch completed.");
            response.setFinalSummary(buildBatchSummary(dataset, total, proved, notProved, stop, resultPath));
            response.setMessage("Batch completed.");
            response.setBatchMode(true);
            response.setBatchTotal(total);
            response.setBatchCompleted(batchResults.size());
            response.setBatchCurrentCase(currentCase);
            response.setBatchProved(proved);
            response.setBatchNotProved(notProved);
            response.setBatchError(0);
            response.setBatchStop(stop);
            response.setBatchResultPath(resultPath);
            response.setBatchResults(batchResults);
            return response;
        } catch (Exception ex) {
            if (isPauseRequested(pauseSignal)) {
                return buildBatchPausedResponse(dataset, logs, batchResults, total, proved, notProved, stop,
                        currentCase, currentAttempt, currentAttemptStates, progressListener, startedAt, "任务已暂停。");
            }
            String readableMessage = normalizeErrorMessage(ex.getMessage());
            append(logs, "Batch analysis failed: " + readableMessage);
            publishBatchProgress(progressListener, "FAILED", 0, 0, 0, 0, 0, 0,
                    "", 0, emptyAttemptStates(), List.of(), startedAt, Instant.now(), "", readableMessage);
            return buildBatchFailedResponse(readableMessage, logs);
        }
    }

    private void publishBatchProgress(BatchProgressListener progressListener,
                                      String status,
                                      int totalCases,
                                      int completedCases,
                                      int provedCount,
                                      int notProvedCount,
                                      int errorCount,
                                      int stopCount,
                                      String currentCaseName,
                                      int currentAttempt,
                                      List<String> currentAttemptStates,
                                      List<NuteraGenerateResponse.BatchCaseResult> results,
                                      Instant startedAt,
                                      Instant finishedAt,
                                      String resultPath,
                                      String message) {
        if (progressListener == null) {
            return;
        }
        List<NuteraGenerateResponse.BatchCaseResult> snapshotResults = new ArrayList<>(results == null ? List.of() : results);
        BatchProgressSnapshot snapshot = new BatchProgressSnapshot(
                status,
                totalCases,
                completedCases,
                provedCount,
                notProvedCount,
                errorCount,
                stopCount,
                nonNull(currentCaseName),
                currentAttempt,
                normalizeAttemptStates(currentAttemptStates),
                snapshotResults,
                startedAt,
                finishedAt,
                nonNull(resultPath),
                nonNull(message)
        );
        progressListener.onProgress(snapshot);
    }

    private NuteraGenerateResponse buildBatchFailedResponse(String message, List<String> logs) {
        NuteraGenerateResponse response = new NuteraGenerateResponse();
        response.setStatus("FAILED");
        response.setCandidateFunction("");
        response.setRawResponse("");
        response.setLog(String.join("\n", logs));
        response.setCheckerStatus("CHECKER_ERROR");
        response.setCheckerMessage(message);
        response.setCheckerRawOutput("");
        response.setCheckerDebugMessage("");
        response.setCheckerTraceTag("");
        response.setCheckerVerdict("CHECKER_ERROR");
        response.setCheckerConclusion("ERROR");
        response.setCheckerCounterexample("");
        response.setCheckerFeedback(message);
        response.setFinalSummary("Batch failed.");
        response.setMessage(message);
        response.setBatchMode(true);
        response.setBatchTotal(0);
        response.setBatchCompleted(0);
        response.setBatchCurrentCase("");
        response.setBatchProved(0);
        response.setBatchNotProved(0);
        response.setBatchError(0);
        response.setBatchStop(0);
        response.setBatchResultPath("");
        response.setBatchResults(List.of());
        return response;
    }

    private NuteraGenerateResponse buildBatchPausedResponse(String dataset,
                                                            List<String> logs,
                                                            List<NuteraGenerateResponse.BatchCaseResult> batchResults,
                                                            int total,
                                                            int proved,
                                                            int notProved,
                                                            int stop,
                                                            String currentCase,
                                                            int currentAttempt,
                                                            List<String> currentAttemptStates,
                                                            BatchProgressListener progressListener,
                                                            Instant startedAt,
                                                            String message) {
        String readableMessage = nonNull(message).isBlank() ? "Batch paused." : message;
        append(logs, readableMessage);
        String resultPath = persistBatchResults(dataset, batchResults, logs, total, proved, notProved, stop);
        Instant finishedAt = Instant.now();
        publishBatchProgress(progressListener, "PAUSED", total, batchResults.size(), proved, notProved, 0, stop,
                currentCase, currentAttempt, currentAttemptStates, batchResults, startedAt, finishedAt, resultPath, readableMessage);

        NuteraGenerateResponse response = new NuteraGenerateResponse();
        response.setStatus("PAUSED");
        response.setCandidateFunction("");
        response.setRawResponse("");
        response.setLog(String.join("\n", logs));
        response.setCheckerStatus("PAUSED");
        response.setCheckerMessage(readableMessage);
        response.setCheckerRawOutput("");
        response.setCheckerDebugMessage("");
        response.setCheckerTraceTag("");
        response.setCheckerVerdict("");
        response.setCheckerConclusion("");
        response.setCheckerCounterexample("");
        response.setCheckerFeedback(readableMessage);
        response.setFinalSummary(buildBatchSummary(dataset, total, proved, notProved, stop, resultPath));
        response.setMessage(readableMessage);
        response.setBatchMode(true);
        response.setBatchTotal(total);
        response.setBatchCompleted(batchResults.size());
        response.setBatchCurrentCase(currentCase);
        response.setBatchProved(proved);
        response.setBatchNotProved(notProved);
        response.setBatchError(0);
        response.setBatchStop(stop);
        response.setBatchResultPath(resultPath);
        response.setBatchResults(batchResults == null ? List.of() : new ArrayList<>(batchResults));
        return response;
    }

    private boolean isPauseRequested(BatchPauseSignal pauseSignal) {
        return pauseSignal != null && pauseSignal.shouldPause();
    }

    private ModelGeneration runModelGeneration(NuteraGenerateRequest request, String prompt, List<String> logs) {
        return runModelGeneration(request, prompt, logs, null);
    }

    private ModelGeneration runModelGeneration(NuteraGenerateRequest request,
                                               String prompt,
                                               List<String> logs,
                                               RefinementContext refinementContext) {
        String userMessage = buildUserMessage(request, refinementContext);
        append(logs, "Calling LLM Chat Completions.");
        SiliconFlowChatService.ChatResult chatResult = chatService.chatCompletion(
                settingsService.getApiKey(),
                request.getModel(),
                prompt,
                userMessage
        );

        String candidate = nonNull(chatResult.getContent()).trim();
        String rankExpression = extractRankExpression(candidate);
        if (rankExpression.isBlank()) {
            throw new IllegalStateException("Failed to extract ranking function expression from model output.");
        }

        append(logs, "Candidate ranking function generated.");
        append(logs, "Extracted ranking function for checker: " + clip(rankExpression, 220));
        return new ModelGeneration(candidate, rankExpression, nonNull(chatResult.getRawResponse()));
    }

    private NuteraGenerateRequest buildCaseRequest(NuteraGenerateRequest baseRequest,
                                                   NuteraCaseSourceResponse caseSource,
                                                   String caseName) {
        NuteraGenerateRequest request = new NuteraGenerateRequest();
        request.setModel(baseRequest.getModel());
        request.setLanguage(baseRequest.getLanguage());
        request.setExtraConfig(baseRequest.getExtraConfig());
        request.setCaseName(caseName);
        request.setFileName(caseSource.getProgramPath());
        request.setCode(nonNull(caseSource.getCode()));
        request.setBatchMode(false);
        return request;
    }

    private NuteraGenerateResponse.BatchCaseResult buildBatchCaseResult(String caseName,
                                                                        String candidateFunction,
                                                                        NuteraCheckerService.CheckerResult checkerResult,
                                                                        String log) {
        NuteraGenerateResponse.BatchCaseResult result = new NuteraGenerateResponse.BatchCaseResult();
        result.setCaseName(caseName);
        result.setCandidateFunction(nonNull(candidateFunction));
        result.setMessage(nonNull(checkerResult.getMessage()));
        result.setCounterexample(nonNull(checkerResult.getCounterexample()));
        result.setCheckerRawOutput(nonNull(checkerResult.getRawOutput()));
        result.setDebugMessage(nonNull(checkerResult.getDebugMessage()));
        result.setTraceTag(nonNull(checkerResult.getTraceTag()));
        result.setCheckerFeedback(buildCheckerFeedback(checkerResult));
        result.setLog(nonNull(log));

        String checkerStatus = nonNull(checkerResult.getStatus()).toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(checkerStatus)) {
            String conclusion = normalizeConclusion(checkerResult);
            String verification = normalizeVerificationStatus(checkerResult);
            result.setExecutionStatus("COMPLETED");
            result.setVerificationStatus(verification.isBlank() ? "CHECKER_ERROR" : verification);
            result.setConclusion(conclusion.isBlank() ? "ERROR" : conclusion);
            return result;
        }

        result.setExecutionStatus("ERROR");
        result.setVerificationStatus("CHECKER_ERROR");
        result.setConclusion("ERROR");
        return result;
    }

    private NuteraGenerateResponse.BatchCaseResult buildBatchErrorResult(String caseName, String message, String log) {
        NuteraGenerateResponse.BatchCaseResult result = new NuteraGenerateResponse.BatchCaseResult();
        result.setCaseName(caseName);
        result.setCandidateFunction("");
        result.setExecutionStatus("ERROR");
        result.setVerificationStatus("CHECKER_ERROR");
        result.setConclusion("ERROR");
        result.setMessage(message);
        result.setCounterexample("");
        result.setCheckerRawOutput("");
        result.setDebugMessage("");
        result.setTraceTag("");
        result.setCheckerFeedback(message);
        result.setLog(nonNull(log));
        return result;
    }

    private String normalizeVerificationStatus(NuteraCheckerService.CheckerResult checkerResult) {
        if (checkerResult == null) {
            return "CHECKER_ERROR";
        }
        String status = nonNull(checkerResult.getStatus()).toUpperCase(Locale.ROOT);
        if ("SKIPPED".equals(status)) {
            return "";
        }
        if (!"COMPLETED".equals(status)) {
            return "CHECKER_ERROR";
        }
        String conclusion = nonNull(checkerResult.getConclusion()).toUpperCase(Locale.ROOT);
        if ("YES".equals(conclusion)) {
            return "PROVED";
        }
        if ("NO".equals(conclusion)) {
            return "NOT_PROVED";
        }
        return "CHECKER_ERROR";
    }

    private String normalizeConclusion(NuteraCheckerService.CheckerResult checkerResult) {
        if (checkerResult == null) {
            return "ERROR";
        }
        String status = nonNull(checkerResult.getStatus()).toUpperCase(Locale.ROOT);
        if ("SKIPPED".equals(status)) {
            return "";
        }
        if (!"COMPLETED".equals(status)) {
            return "ERROR";
        }
        String conclusion = nonNull(checkerResult.getConclusion()).toUpperCase(Locale.ROOT);
        if ("YES".equals(conclusion) || "NO".equals(conclusion)) {
            return conclusion;
        }
        return "ERROR";
    }

    private String persistBatchResults(String dataset,
                                       List<NuteraGenerateResponse.BatchCaseResult> results,
                                       List<String> logs,
                                       int total,
                                       int proved,
                                       int notProved,
                                       int stop) {
        try {
            Path outputDir = runtimeRoot.resolve("output").resolve("batch-results").normalize();
            Files.createDirectories(outputDir);
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now());
            String safeDataset = nonNull(dataset).replaceAll("[^A-Za-z0-9_-]", "_");
            Path jsonPath = outputDir.resolve("batch-" + safeDataset + "-" + timestamp + ".json").normalize();
            Path csvPath = outputDir.resolve("batch-" + safeDataset + "-" + timestamp + ".csv").normalize();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("dataset", dataset);
            payload.put("generatedAt", Instant.now().toString());
            payload.put("total", total);
            payload.put("proved", proved);
            payload.put("notProved", notProved);
            payload.put("stop", stop);
            payload.put("results", results);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), payload);

            List<String> csvLines = new ArrayList<>();
            csvLines.add("caseName,candidateFunction,executionStatus,verificationStatus,conclusion,counterexample,message,attemptCount,finalStatus,stopReason");
            for (NuteraGenerateResponse.BatchCaseResult item : results) {
                csvLines.add(String.join(",",
                        toCsv(item.getCaseName()),
                        toCsv(item.getCandidateFunction()),
                        toCsv(item.getExecutionStatus()),
                        toCsv(item.getVerificationStatus()),
                        toCsv(item.getConclusion()),
                        toCsv(item.getCounterexample()),
                        toCsv(item.getMessage()),
                        toCsv(String.valueOf(item.getAttemptCount())),
                        toCsv(item.getFinalStatus()),
                        toCsv(item.getStopReason())
                ));
            }
            Files.write(csvPath, csvLines, StandardCharsets.UTF_8);
            String pathText = toDisplayPath(jsonPath) + " | " + toDisplayPath(csvPath);
            append(logs, "Batch results saved: " + pathText);
            return pathText;
        } catch (Exception ex) {
            append(logs, "Batch result persistence failed: " + normalizeErrorMessage(ex.getMessage()));
            return "";
        }
    }

    private String toCsv(String value) {
        String text = nonNull(value);
        boolean needQuote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!needQuote) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String buildBatchSummary(String dataset,
                                     int total,
                                     int proved,
                                     int notProved,
                                     int stop,
                                     String resultPath) {
        return "Dataset: " + nonNull(dataset) + "\n"
                + "Total cases: " + total + "\n"
                + "PROVED: " + proved + "\n"
                + "NOT_PROVED: " + notProved + "\n"
                + "STOP: " + stop + "\n"
                + "Artifacts: " + (resultPath.isBlank() ? "(not saved)" : resultPath);
    }

    private void appendCaseSourceLog(List<String> logs, String caseLog, String prefix) {
        if (caseLog == null || caseLog.isBlank()) {
            return;
        }
        for (String line : caseLog.split("\\R")) {
            append(logs, prefix + " " + line);
        }
    }

    private String buildCaseName(NuteraCaseSourceResponse caseSource, int index) {
        if (caseSource == null) {
            return "entry-" + (index + 1);
        }
        String className = nonNull(caseSource.getProgramClass()).trim();
        String method = nonNull(caseSource.getProgramFunction()).trim();
        if (!className.isBlank()) {
            return method.isBlank() ? className : className + "#" + method;
        }
        String path = nonNull(caseSource.getProgramPath()).trim();
        if (!path.isBlank()) {
            return path;
        }
        return "entry-" + (index + 1);
    }

    private String buildUserMessage(NuteraGenerateRequest request) {
        return buildUserMessage(request, null);
    }

    private String buildUserMessage(NuteraGenerateRequest request, RefinementContext refinementContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate candidate ranking function(s) for the following program.\n\n");
        sb.append("Language: ").append(nonNull(request.getLanguage())).append("\n");
        sb.append("Case: ").append(nonNull(request.getCaseName())).append("\n");
        sb.append("File: ").append(nonNull(request.getFileName())).append("\n");
        sb.append("Extra config: ").append(nonNull(request.getExtraConfig())).append("\n\n");
        sb.append("Code:\n");
        sb.append("```").append(nonNull(request.getLanguage())).append("\n");
        sb.append(nonNull(request.getCode())).append("\n");
        sb.append("```\n");
        if (refinementContext != null && !refinementContext.isEmpty()) {
            sb.append("\n");
            sb.append("Previous attempt index: ").append(refinementContext.getAttempt()).append("\n");
            if (!refinementContext.getCandidateFunction().isBlank()) {
                sb.append("Previous candidate:\n");
                sb.append("```\n").append(refinementContext.getCandidateFunction()).append("\n```\n");
            }
            if (!refinementContext.getCheckerFeedback().isBlank()) {
                sb.append("Checker feedback from previous attempt:\n");
                sb.append("```\n").append(refinementContext.getCheckerFeedback()).append("\n```\n");
            }
            if (!refinementContext.getCounterexample().isBlank()) {
                sb.append("Counterexample from previous attempt:\n");
                sb.append(refinementContext.getCounterexample()).append("\n");
            }
            if (!refinementContext.getFailureReason().isBlank()) {
                sb.append("Failure reason from previous attempt:\n");
                sb.append(refinementContext.getFailureReason()).append("\n");
            }
            sb.append("Please improve the ranking function based on this feedback. ");
            sb.append("Do not repeat the previous candidate unchanged.\n");
        }
        return sb.toString();
    }

    private List<String> emptyAttemptStates() {
        return new ArrayList<>(List.of("", "", "", "", ""));
    }

    private List<String> normalizeAttemptStates(List<String> states) {
        List<String> normalized = emptyAttemptStates();
        if (states == null) {
            return normalized;
        }
        int max = Math.min(MAX_BATCH_ATTEMPTS, states.size());
        for (int i = 0; i < max; i++) {
            String value = nonNull(states.get(i)).trim().toUpperCase(Locale.ROOT);
            if ("PROVED".equals(value) || "NOT_PROVED".equals(value) || "STOP".equals(value)) {
                normalized.set(i, value);
            }
        }
        return normalized;
    }

    private boolean isNoReluSolutionCandidate(String candidateFunction) {
        String text = nonNull(candidateFunction);
        if (text.isBlank()) {
            return false;
        }
        if (isNoReluMarkerToken(text)) {
            return true;
        }

        Matcher fenced = Pattern.compile("```(?:[a-zA-Z0-9_-]+)?\\s*([\\s\\S]*?)```").matcher(text);
        while (fenced.find()) {
            if (isNoReluMarkerToken(fenced.group(1))) {
                return true;
            }
        }

        String[] lines = text.split("\\R");
        for (String line : lines) {
            if (isNoReluMarkerToken(line)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNoReluMarkerToken(String raw) {
        String text = nonNull(raw).trim();
        if (text.isBlank()) {
            return false;
        }

        String normalized = text.replaceFirst("^[\\s>*-]*\\d*[.)]?\\s*", "").trim();
        normalized = normalized.replaceFirst("(?i)^(candidate(?:_function)?|ranking\\s*function|rank|result)\\s*[:=]\\s*", "").trim();
        normalized = normalized.replace("`", "").trim();

        while (normalized.length() >= 2) {
            boolean stripped = false;
            if ((normalized.startsWith("\"") && normalized.endsWith("\""))
                    || (normalized.startsWith("'") && normalized.endsWith("'"))
                    || (normalized.startsWith("[") && normalized.endsWith("]"))
                    || (normalized.startsWith("(") && normalized.endsWith(")"))
                    || (normalized.startsWith("{") && normalized.endsWith("}"))) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
                stripped = true;
            }
            if (!stripped) {
                break;
            }
        }

        String marker = normalized.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return "NO_RELU_SOLUTION".equals(marker);
    }

    private boolean isStopCaseResult(NuteraGenerateResponse.BatchCaseResult result) {
        if (result == null) {
            return false;
        }
        if (!nonNull(result.getStopReason()).isBlank()) {
            return true;
        }
        String verificationStatus = nonNull(result.getVerificationStatus()).toUpperCase(Locale.ROOT);
        if ("STOP".equals(verificationStatus)) {
            return true;
        }
        String finalStatus = nonNull(result.getFinalStatus()).toUpperCase(Locale.ROOT);
        if ("STOP".equals(finalStatus)) {
            return true;
        }
        List<String> states = result.getAttemptStates();
        if (states == null) {
            return false;
        }
        return states.stream().anyMatch(state -> "STOP".equalsIgnoreCase(nonNull(state)));
    }

    private NuteraGenerateResponse.BatchCaseResult buildProvedCaseResult(String caseName,
                                                                          String candidateFunction,
                                                                          NuteraCheckerService.CheckerResult checkerResult,
                                                                          String log,
                                                                          int attemptCount,
                                                                          List<String> attemptStates) {
        NuteraGenerateResponse.BatchCaseResult result = new NuteraGenerateResponse.BatchCaseResult();
        result.setCaseName(caseName);
        result.setCandidateFunction(nonNull(candidateFunction));
        result.setExecutionStatus("COMPLETED");
        result.setVerificationStatus("PROVED");
        result.setConclusion("YES");
        result.setMessage(nonNull(checkerResult.getMessage()).isBlank()
                ? "Checker proved termination."
                : checkerResult.getMessage());
        result.setCounterexample(nonNull(checkerResult.getCounterexample()));
        result.setCheckerRawOutput(nonNull(checkerResult.getRawOutput()));
        result.setDebugMessage(nonNull(checkerResult.getDebugMessage()));
        result.setTraceTag(nonNull(checkerResult.getTraceTag()));
        result.setCheckerFeedback(buildCheckerFeedback(checkerResult));
        result.setAttemptCount(Math.max(1, attemptCount));
        result.setFinalStatus("PROVED");
        result.setStopReason("");
        result.setAttemptStates(normalizeAttemptStates(attemptStates));
        result.setLog(nonNull(log));
        return result;
    }

    private NuteraGenerateResponse.BatchCaseResult buildNotProvedCaseResult(String caseName,
                                                                             String candidateFunction,
                                                                             String checkerRawOutput,
                                                                             String message,
                                                                             String counterexample,
                                                                             String log,
                                                                             int attemptCount,
                                                                             List<String> attemptStates,
                                                                             String debugMessage,
                                                                             String traceTag) {
        NuteraGenerateResponse.BatchCaseResult result = new NuteraGenerateResponse.BatchCaseResult();
        result.setCaseName(caseName);
        result.setCandidateFunction(nonNull(candidateFunction));
        result.setExecutionStatus("COMPLETED");
        result.setVerificationStatus("NOT_PROVED");
        result.setConclusion("NO");
        result.setMessage(nonNull(message).isBlank()
                ? "Candidate function could not prove termination."
                : message);
        result.setCounterexample(nonNull(counterexample));
        result.setCheckerRawOutput(nonNull(checkerRawOutput));
        result.setDebugMessage(nonNull(debugMessage));
        result.setTraceTag(nonNull(traceTag));
        if (!checkerRawOutput.isBlank()) {
            result.setCheckerFeedback(checkerRawOutput);
        } else {
            result.setCheckerFeedback(nonNull(message));
        }
        result.setAttemptCount(Math.max(1, attemptCount));
        result.setFinalStatus("NOT_PROVED");
        result.setStopReason("");
        result.setAttemptStates(normalizeAttemptStates(attemptStates));
        result.setLog(nonNull(log));
        return result;
    }

    private NuteraGenerateResponse.BatchCaseResult buildStopCaseResult(String caseName,
                                                                       String candidateFunction,
                                                                       String stopReason,
                                                                       String log,
                                                                       int attemptCount,
                                                                       List<String> attemptStates) {
        NuteraGenerateResponse.BatchCaseResult result = new NuteraGenerateResponse.BatchCaseResult();
        result.setCaseName(caseName);
        result.setCandidateFunction(nonNull(candidateFunction));
        result.setExecutionStatus("COMPLETED");
        result.setVerificationStatus("NOT_PROVED");
        result.setConclusion("NO");
        result.setMessage("LLM returned NO_RELU_SOLUTION.");
        result.setCounterexample("");
        result.setCheckerRawOutput("");
        result.setDebugMessage("");
        result.setTraceTag("");
        result.setCheckerFeedback("NO_RELU_SOLUTION");
        result.setAttemptCount(Math.max(1, attemptCount));
        result.setFinalStatus("NOT_PROVED");
        result.setStopReason(nonNull(stopReason).isBlank() ? "NO_RELU_SOLUTION" : stopReason);
        result.setAttemptStates(normalizeAttemptStates(attemptStates));
        result.setLog(nonNull(log));
        return result;
    }

    private static final class RefinementContext {
        private final String candidateFunction;
        private final String checkerFeedback;
        private final String counterexample;
        private final String failureReason;
        private final int attempt;

        private RefinementContext(String candidateFunction,
                                  String checkerFeedback,
                                  String counterexample,
                                  String failureReason,
                                  int attempt) {
            this.candidateFunction = candidateFunction == null ? "" : candidateFunction;
            this.checkerFeedback = checkerFeedback == null ? "" : checkerFeedback;
            this.counterexample = counterexample == null ? "" : counterexample;
            this.failureReason = failureReason == null ? "" : failureReason;
            this.attempt = Math.max(1, attempt);
        }

        private static RefinementContext fromFailure(String candidateFunction,
                                                     String checkerFeedback,
                                                     String counterexample,
                                                     String failureReason,
                                                     int attempt) {
            return new RefinementContext(candidateFunction, checkerFeedback, counterexample, failureReason, attempt);
        }

        private boolean isEmpty() {
            return candidateFunction.isBlank()
                    && checkerFeedback.isBlank()
                    && counterexample.isBlank()
                    && failureReason.isBlank();
        }

        private String getCandidateFunction() {
            return candidateFunction;
        }

        private String getCheckerFeedback() {
            return checkerFeedback;
        }

        private String getCounterexample() {
            return counterexample;
        }

        private String getFailureReason() {
            return failureReason;
        }

        private int getAttempt() {
            return attempt;
        }
    }

    private String nonNull(String value) {
        return value == null ? "" : value;
    }

    private void append(List<String> logs, String message) {
        logs.add("[" + Instant.now() + "] " + message);
    }

    private String normalizeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown error.";
        }
        String sanitized = LEADING_CHECKER_TRACE_TAG.matcher(message).replaceFirst("").trim();
        return sanitized.isBlank() ? "Unknown error." : sanitized;
    }

    private String extractRankExpression(String candidate) {
        String text = nonNull(candidate).trim();
        if (text.isBlank()) {
            return "";
        }

        Matcher fenced = Pattern.compile("```(?:[a-zA-Z0-9_-]+)?\\s*([\\s\\S]*?)```").matcher(text);
        if (fenced.find()) {
            text = fenced.group(1).trim();
        }

        String vectorExpr = firstRegex(text, "\\[[^\\[\\]]*ReLU[^\\[\\]]*\\]");
        if (!vectorExpr.isBlank()) {
            return vectorExpr.trim();
        }

        String[] lines = text.split("\\R");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isBlank()) {
                continue;
            }
            line = line.replaceFirst("^[\\-*\\d.)\\s]+", "").trim();
            line = line.replace("`", "").trim();
            if (line.toLowerCase().startsWith("candidate")) {
                int colon = line.indexOf(':');
                if (colon >= 0 && colon + 1 < line.length()) {
                    line = line.substring(colon + 1).trim();
                }
            }
            if (line.matches("^[A-Za-z_][\\w\\s]*\\(?.*?\\)?\\s*=\\s*.+$")) {
                int eq = line.indexOf('=');
                line = line.substring(eq + 1).trim();
            }
            if (line.contains("ReLU(") || line.contains("relu(") || line.contains("Max(") || line.contains("max(")) {
                return line;
            }
        }

        String first = lines.length > 0 ? lines[0].trim() : text;
        if (first.matches("^[A-Za-z_][\\w\\s]*\\(?.*?\\)?\\s*=\\s*.+$")) {
            int eq = first.indexOf('=');
            first = first.substring(eq + 1).trim();
        }
        return first;
    }

    private String firstRegex(String text, String pattern) {
        Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return "";
    }

    private String buildCheckerFeedback(NuteraCheckerService.CheckerResult checkerResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("Execution Status: ").append(nonNull(checkerResult.getStatus())).append("\n");
        if (!nonNull(checkerResult.getVerdict()).isBlank()) {
            sb.append("Verdict: ").append(nonNull(checkerResult.getVerdict())).append("\n");
        }
        if (!nonNull(checkerResult.getConclusion()).isBlank()) {
            sb.append("Conclusion: ").append(nonNull(checkerResult.getConclusion())).append("\n");
        }
        sb.append("Message: ").append(nonNull(checkerResult.getMessage())).append("\n\n");
        if (!nonNull(checkerResult.getCounterexample()).isBlank()) {
            sb.append("Counterexample:\n").append(nonNull(checkerResult.getCounterexample())).append("\n\n");
        }
        sb.append(nonNull(checkerResult.getRawOutput()).isBlank()
                ? "(No checker raw output)"
                : checkerResult.getRawOutput());
        return sb.toString();
    }

    private String buildFinalSummary(NuteraCaseSourceResponse caseSource,
                                     NuteraCheckerService.CheckerResult checkerResult) {
        StringBuilder sb = new StringBuilder();
        if (caseSource != null) {
            sb.append("Dataset: ").append(nonNull(caseSource.getDataset())).append("\n");
            sb.append("CSV: ").append(nonNull(caseSource.getCsvPath())).append("\n");
            sb.append("Program: ").append(nonNull(caseSource.getProgramClass()));
            if (!nonNull(caseSource.getProgramFunction()).isBlank()) {
                sb.append("#").append(caseSource.getProgramFunction());
            }
            sb.append("\n");
            sb.append("Jar: ").append(nonNull(caseSource.getProgramBinaryPath())).append("\n");
            sb.append("Source: ").append(nonNull(caseSource.getProgramPath())).append("\n");
        } else {
            sb.append("Dataset: (not selected)\n");
        }
        sb.append("Checker status: ").append(nonNull(checkerResult.getStatus())).append("\n");
        sb.append("Checker message: ").append(nonNull(checkerResult.getMessage()));
        return sb.toString();
    }

    private String clip(String text, int maxLen) {
        String value = nonNull(text);
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    private String toDisplayPath(Path path) {
        try {
            return projectRoot.relativize(path).toString().replace("\\", "/");
        } catch (Exception ignored) {
            return path.toAbsolutePath().normalize().toString().replace("\\", "/");
        }
    }

    private Path resolveProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (Files.exists(current.resolve("runtimes"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.exists(parent.resolve("runtimes"))) {
            return parent;
        }
        return current;
    }

    private Path resolveRuntimeRoot(String configuredRuntimePath, Path baseRoot) {
        Path configured = Path.of(configuredRuntimePath);
        if (configured.isAbsolute()) {
            return configured.normalize();
        }
        Path direct = baseRoot.resolve(configured).normalize();
        if (Files.exists(direct)) {
            return direct;
        }
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path fallback = current.resolve(configured).normalize();
        if (Files.exists(fallback)) {
            return fallback;
        }
        return direct;
    }

    private static final class ModelGeneration {
        private final String candidateFunction;
        private final String rankExpression;
        private final String rawResponse;

        private ModelGeneration(String candidateFunction, String rankExpression, String rawResponse) {
            this.candidateFunction = candidateFunction;
            this.rankExpression = rankExpression;
            this.rawResponse = rawResponse;
        }
    }

    @FunctionalInterface
    public interface BatchProgressListener {
        void onProgress(BatchProgressSnapshot snapshot);
    }

    @FunctionalInterface
    public interface BatchPauseSignal {
        boolean shouldPause();
    }

    public static final class BatchProgressSnapshot {
        private final String status;
        private final int totalCases;
        private final int completedCases;
        private final int provedCount;
        private final int notProvedCount;
        private final int errorCount;
        private final int stopCount;
        private final String currentCaseName;
        private final int currentAttempt;
        private final List<String> currentAttemptStates;
        private final List<NuteraGenerateResponse.BatchCaseResult> results;
        private final Instant startedAt;
        private final Instant finishedAt;
        private final String resultPath;
        private final String message;

        public BatchProgressSnapshot(String status,
                                     int totalCases,
                                     int completedCases,
                                     int provedCount,
                                     int notProvedCount,
                                     int errorCount,
                                     int stopCount,
                                     String currentCaseName,
                                     int currentAttempt,
                                     List<String> currentAttemptStates,
                                     List<NuteraGenerateResponse.BatchCaseResult> results,
                                     Instant startedAt,
                                     Instant finishedAt,
                                     String resultPath,
                                     String message) {
            this.status = status;
            this.totalCases = totalCases;
            this.completedCases = completedCases;
            this.provedCount = provedCount;
            this.notProvedCount = notProvedCount;
            this.errorCount = errorCount;
            this.stopCount = stopCount;
            this.currentCaseName = currentCaseName == null ? "" : currentCaseName;
            this.currentAttempt = Math.max(0, currentAttempt);
            this.currentAttemptStates = currentAttemptStates == null ? List.of() : new ArrayList<>(currentAttemptStates);
            this.results = results == null ? List.of() : new ArrayList<>(results);
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.resultPath = resultPath == null ? "" : resultPath;
            this.message = message == null ? "" : message;
        }

        public String getStatus() { return status; }
        public int getTotalCases() { return totalCases; }
        public int getCompletedCases() { return completedCases; }
        public int getProvedCount() { return provedCount; }
        public int getNotProvedCount() { return notProvedCount; }
        public int getErrorCount() { return errorCount; }
        public int getStopCount() { return stopCount; }
        public String getCurrentCaseName() { return currentCaseName; }
        public int getCurrentAttempt() { return currentAttempt; }
        public List<String> getCurrentAttemptStates() { return new ArrayList<>(currentAttemptStates); }
        public List<NuteraGenerateResponse.BatchCaseResult> getResults() { return new ArrayList<>(results); }
        public Instant getStartedAt() { return startedAt; }
        public Instant getFinishedAt() { return finishedAt; }
        public String getResultPath() { return resultPath; }
        public String getMessage() { return message; }
    }
}
