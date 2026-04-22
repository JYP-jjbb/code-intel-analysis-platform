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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NuteraLlmService {
    private static final int MAX_SINGLE_ATTEMPTS = 3;
    private static final int MAX_BATCH_ATTEMPTS = 5;
    private static final String AFFINE_TEMPLATE_ERROR_CODE = "AFFINE_TEMPLATE_PRECHECK_FAILED";
    private static final String AFFINE_TEMPLATE_USER_MESSAGE = "候选函数不符合当前验证器支持的线性模板，系统已自动重试。";
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
        int finalAttemptCount = 0;

        try {
            append(logs, "Loading prompt file.");
            String prompt = promptLoaderService.loadPrompt();
            append(logs, "Prompt loaded.");
            NuteraCaseSourceResponse caseSource = null;
            String dataset = nonNull(request.getCaseName()).trim();
            boolean checkerEnabled = !(dataset.isBlank() || "none".equalsIgnoreCase(dataset));
            if (checkerEnabled) {
                append(logs, "Loading case metadata for checker: " + dataset);
                caseSource = caseSourceService.loadCaseSource(dataset, 0);
                appendCaseSourceLog(logs, caseSource.getLog(), "[case-source]");
            }
            RefinementContext refinementContext = null;
            ModelGeneration lastGeneration = null;
            NuteraCheckerService.CheckerResult lastCheckerResult = NuteraCheckerService.CheckerResult.skipped("");

            for (int attempt = 1; attempt <= MAX_SINGLE_ATTEMPTS; attempt++) {
                String module = "nutera.single.candidate-generation.attempt-" + attempt;
                String previousErrorSummary = refinementContext == null ? "" : nonNull(refinementContext.getFailureReason());
                append(logs, "Single verification attempt " + attempt + "/" + MAX_SINGLE_ATTEMPTS
                        + " started. module=" + module + ", mode=chat, previous_error_summary="
                        + (previousErrorSummary.isBlank() ? "-" : clip(previousErrorSummary, 320)));
                try {
                    ModelGeneration generation = runModelGeneration(request, prompt, logs, refinementContext, module);
                    lastGeneration = generation;
                    finalAttemptCount = attempt;
                    NuteraCheckerService.CheckerResult checkerResult;
                    if (!checkerEnabled) {
                        checkerResult = NuteraCheckerService.CheckerResult.skipped(
                                "No dataset selected. Checker requires dataset program metadata."
                        );
                        append(logs, "Checker skipped: no dataset selected.");
                        return buildSingleResultResponse("SUCCESS", "Generation succeeded.", generation, checkerResult, caseSource, logs, attempt);
                    }

                    append(logs, "Attempt " + attempt + " candidate expression: " + clip(generation.rankExpression, 220));
                    TemplatePrecheckResult precheck = precheckAffineReluTemplate(generation.rankExpression);
                    if (!precheck.isValid()) {
                        String detail = precheck.getDetail();
                        append(logs, "Attempt " + attempt + " candidate precheck failed: " + detail);
                        if (attempt < MAX_SINGLE_ATTEMPTS) {
                            append(logs, "Attempt " + attempt + " precheck failed. Retrying with checker template feedback.");
                            refinementContext = RefinementContext.fromFailure(
                                    generation.candidateFunction,
                                    precheckFeedback(detail),
                                    "",
                                    AFFINE_TEMPLATE_ERROR_CODE + ": " + detail,
                                    attempt
                            );
                            continue;
                        }
                        checkerResult = NuteraCheckerService.CheckerResult.error(
                                AFFINE_TEMPLATE_ERROR_CODE + ": " + AFFINE_TEMPLATE_USER_MESSAGE,
                                detail
                        );
                        String finalFailure = "已自动尝试 " + MAX_SINGLE_ATTEMPTS + " 次，均未生成符合线性模板要求的候选函数。";
                        append(logs, finalFailure);
                        return buildSingleResultResponse(
                                "FAILED",
                                finalFailure,
                                generation,
                                checkerResult,
                                caseSource,
                                logs,
                                attempt
                        );
                    }
                    append(logs, "Attempt " + attempt + " candidate precheck passed.");

                    checkerResult = checkerService.runChecker(generation.rankExpression, caseSource, logs);
                    lastCheckerResult = checkerResult;
                    String conclusion = normalizeConclusion(checkerResult);
                    if ("YES".equalsIgnoreCase(conclusion)) {
                        append(logs, "Single verification succeeded at attempt " + attempt + ".");
                        return buildSingleResultResponse("SUCCESS", "Generation succeeded.", generation, checkerResult, caseSource, logs, attempt);
                    }

                    String checkerFailure = nonNull(checkerResult.getMessage()).isBlank()
                            ? "Checker could not prove termination."
                            : checkerResult.getMessage();
                    append(logs, "Attempt " + attempt + " checker not proved: " + checkerFailure);
                    if (attempt < MAX_SINGLE_ATTEMPTS) {
                        append(logs, "Attempt " + attempt + " checker not proved. Retrying with checker feedback.");
                        refinementContext = RefinementContext.fromFailure(
                                generation.candidateFunction,
                                buildCheckerFeedback(checkerResult),
                                checkerResult.getCounterexample(),
                                checkerFailure,
                                attempt
                        );
                        continue;
                    }

                    append(logs, "Single verification exhausted attempts.");
                    return buildSingleResultResponse(
                            "FAILED",
                            "Single verification failed after " + MAX_SINGLE_ATTEMPTS + " attempts.",
                            generation,
                            checkerResult,
                            caseSource,
                            logs,
                            attempt
                    );
                } catch (Exception ex) {
                    String readableMessage = normalizeErrorMessage(ex.getMessage());
                    finalAttemptCount = attempt;
                    append(logs, "Attempt " + attempt + " failed: " + readableMessage);
                    if (attempt < MAX_SINGLE_ATTEMPTS) {
                        append(logs, "Attempt " + attempt + " failed. Retrying with previous error summary.");
                        String previousCandidate = lastGeneration == null ? "" : nonNull(lastGeneration.candidateFunction);
                        String checkerFeedback = lastCheckerResult == null ? "" : buildCheckerFeedback(lastCheckerResult);
                        String counterexample = lastCheckerResult == null ? "" : nonNull(lastCheckerResult.getCounterexample());
                        refinementContext = RefinementContext.fromFailure(
                                previousCandidate,
                                checkerFeedback,
                                counterexample,
                                readableMessage,
                                attempt
                        );
                        continue;
                    }

                    ModelGeneration fallbackGeneration = lastGeneration == null ? new ModelGeneration("", "", "") : lastGeneration;
                    NuteraCheckerService.CheckerResult fallbackChecker =
                            lastCheckerResult == null
                                    ? NuteraCheckerService.CheckerResult.error("CALL_FAILED: " + readableMessage, readableMessage)
                                    : lastCheckerResult;
                    return buildSingleFailureResponse(fallbackGeneration, fallbackChecker, caseSource, logs, readableMessage, attempt);
                }
            }

            return buildSingleFailureResponse(
                    lastGeneration == null ? new ModelGeneration("", "", "") : lastGeneration,
                    lastCheckerResult,
                    caseSource,
                    logs,
                    "Single verification failed after retries.",
                    finalAttemptCount <= 0 ? MAX_SINGLE_ATTEMPTS : finalAttemptCount
            );
        } catch (Exception ex) {
            String readableMessage = normalizeErrorMessage(ex.getMessage());
            append(logs, "Generation failed: " + readableMessage);
            return buildSingleFailureResponse(
                    new ModelGeneration("", "", ""),
                    NuteraCheckerService.CheckerResult.error("VALIDATION_ERROR: " + readableMessage, readableMessage),
                    null,
                    logs,
                    readableMessage,
                    finalAttemptCount
            );
        }
    }

    private NuteraGenerateResponse buildSingleResultResponse(String status,
                                                             String message,
                                                             ModelGeneration generation,
                                                             NuteraCheckerService.CheckerResult checkerResult,
                                                             NuteraCaseSourceResponse caseSource,
                                                             List<String> logs,
                                                             int attemptCount) {
        NuteraCheckerService.CheckerResult safeChecker =
                checkerResult == null
                        ? NuteraCheckerService.CheckerResult.error("VALIDATION_ERROR: " + nonNull(message), nonNull(message))
                        : checkerResult;
        NuteraGenerateResponse response = new NuteraGenerateResponse();
        response.setStatus(nonNull(status).isBlank() ? "FAILED" : status);
        response.setCandidateFunction(generation == null ? "" : nonNull(generation.candidateFunction));
        response.setRawResponse(generation == null ? "" : nonNull(generation.rawResponse));
        response.setLog(String.join("\n", logs));
        response.setCheckerStatus(nonNull(safeChecker.getStatus()));
        response.setCheckerMessage(nonNull(safeChecker.getMessage()));
        response.setCheckerRawOutput(nonNull(safeChecker.getRawOutput()));
        response.setCheckerDebugMessage(nonNull(safeChecker.getDebugMessage()));
        response.setCheckerTraceTag(nonNull(safeChecker.getTraceTag()));
        response.setCheckerVerdict(normalizeVerificationStatus(safeChecker));
        response.setCheckerConclusion(normalizeConclusion(safeChecker));
        response.setCheckerCounterexample(nonNull(safeChecker.getCounterexample()));
        response.setCheckerFeedback(buildCheckerFeedback(safeChecker));
        response.setFinalSummary(buildFinalSummary(caseSource, safeChecker));
        response.setMessage(nonNull(message).isBlank() ? "Generation finished." : message);
        response.setAttemptCount(Math.max(0, attemptCount));
        response.setMaxAttempts(MAX_SINGLE_ATTEMPTS);
        response.setBatchMode(false);
        response.setBatchResults(List.of());
        return response;
    }

    private NuteraGenerateResponse buildSingleFailureResponse(ModelGeneration generation,
                                                              NuteraCheckerService.CheckerResult checkerResult,
                                                              NuteraCaseSourceResponse caseSource,
                                                              List<String> logs,
                                                              String failureMessage,
                                                              int attemptCount) {
        String message = nonNull(failureMessage).isBlank() ? "Generation failed." : failureMessage;
        NuteraCheckerService.CheckerResult fallbackChecker =
                checkerResult == null
                        ? NuteraCheckerService.CheckerResult.error("CALL_FAILED: " + message, message)
                        : checkerResult;
        return buildSingleResultResponse("FAILED", message, generation, fallbackChecker, caseSource, logs, attemptCount);
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
                            generation = runModelGeneration(
                                    caseRequest,
                                    prompt,
                                    caseLogs,
                                    refinementContext,
                                    "nutera.batch.candidate-generation.attempt-" + attempt
                            );
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

                        TemplatePrecheckResult precheck = precheckAffineReluTemplate(generation.rankExpression);
                        if (!precheck.isValid()) {
                            String detail = precheck.getDetail();
                            attemptStates.set(attempt - 1, "NOT_PROVED");
                            append(caseLogs, "Attempt " + attempt + " candidate precheck failed: " + detail);
                            refinementContext = RefinementContext.fromFailure(
                                    generation.candidateFunction,
                                    precheckFeedback(detail),
                                    "",
                                    AFFINE_TEMPLATE_ERROR_CODE + ": " + detail,
                                    attempt
                            );
                            if (attempt == MAX_BATCH_ATTEMPTS) {
                                finalCaseResult = buildNotProvedCaseResult(
                                        caseName,
                                        generation.candidateFunction,
                                        detail,
                                        AFFINE_TEMPLATE_USER_MESSAGE,
                                        "",
                                        String.join("\n", caseLogs),
                                        attempt,
                                        attemptStates,
                                        AFFINE_TEMPLATE_ERROR_CODE,
                                        "affine-template-precheck"
                                );
                            }
                            continue;
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
        return runModelGeneration(request, prompt, logs, null, request.isBatchMode()
                ? "nutera.batch.candidate-generation"
                : "nutera.single.candidate-generation");
    }

    private ModelGeneration runModelGeneration(NuteraGenerateRequest request,
                                               String prompt,
                                               List<String> logs,
                                               RefinementContext refinementContext) {
        return runModelGeneration(
                request,
                prompt,
                logs,
                refinementContext,
                request.isBatchMode() ? "nutera.batch.candidate-generation" : "nutera.single.candidate-generation"
        );
    }

    private ModelGeneration runModelGeneration(NuteraGenerateRequest request,
                                               String prompt,
                                               List<String> logs,
                                               RefinementContext refinementContext,
                                               String requestModule) {
        String userMessage = buildUserMessage(request, refinementContext);
        String previousErrorSummary = refinementContext == null ? "" : nonNull(refinementContext.getFailureReason());
        append(logs, "Calling LLM Chat Completions. module=" + nonNull(requestModule)
                + ", previous_error_summary=" + (previousErrorSummary.isBlank() ? "-" : clip(previousErrorSummary, 320)));
        SiliconFlowChatService.ChatResult chatResult = chatService.chatCompletion(
                settingsService.getApiKey(),
                request.getModel(),
                prompt,
                userMessage,
                requestModule
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
        sb.append("\n");
        sb.append("STRICT TEMPLATE CONSTRAINTS (MUST FOLLOW):\n");
        sb.append("1) Candidate must be affine/linear ReLU template only.\n");
        sb.append("2) Allowed forms only:\n");
        sb.append("   - ReLU(a1*x1 + a2*x2 + ... + b)\n");
        sb.append("   - [ReLU(a1*x1 + ... + b1), ReLU(a1*x1 + ... + b2), ...]\n");
        sb.append("3) Forbidden structures:\n");
        sb.append("   - variable*variable (x*y, X*y, Y*x)\n");
        sb.append("   - high-order terms (x^2, y^2)\n");
        sb.append("   - division/fraction, abs, min/max, conditional expression\n");
        sb.append("   - any non-affine transform\n");
        sb.append("4) Prioritize simple and checker-verifiable linear expressions.\n");
        sb.append("5) Return only candidate function expression, no explanation, no markdown list.\n");
        sb.append("6) If no valid affine ReLU template can be found, return exactly: NO_RELU_SOLUTION\n");
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

    private String precheckFeedback(String detail) {
        return "Checker template precheck failed: " + detail + "\n"
                + "Allowed template: ReLU(a1*x1 + ... + b) or [ReLU(...), ReLU(...)] with affine terms only.\n"
                + "Forbidden: variable*variable, high-order terms, division/fraction, abs/min/max, conditional operators.";
    }

    private TemplatePrecheckResult precheckAffineReluTemplate(String rankExpression) {
        String expression = nonNull(rankExpression).trim();
        if (expression.isBlank()) {
            return TemplatePrecheckResult.invalid("empty expression.");
        }
        if (isNoReluSolutionCandidate(expression)) {
            return TemplatePrecheckResult.valid();
        }
        String compact = expression.replace("`", "").trim();
        compact = unwrapOuterParentheses(compact);
        if (compact.isBlank()) {
            return TemplatePrecheckResult.invalid("empty expression after normalization.");
        }

        List<String> reluTerms;
        if (compact.startsWith("[") && compact.endsWith("]")) {
            String payload = compact.substring(1, compact.length() - 1).trim();
            if (payload.isBlank()) {
                return TemplatePrecheckResult.invalid("vector expression is empty.");
            }
            reluTerms = splitTopLevel(payload, ',');
            if (reluTerms.isEmpty()) {
                return TemplatePrecheckResult.invalid("vector expression has no ReLU terms.");
            }
        } else {
            reluTerms = List.of(compact);
        }

        for (String rawTerm : reluTerms) {
            String term = unwrapOuterParentheses(nonNull(rawTerm).trim());
            Matcher reluMatcher = Pattern.compile("(?i)^relu\\s*\\((.*)\\)$").matcher(term);
            if (!reluMatcher.matches()) {
                return TemplatePrecheckResult.invalid("term is not ReLU(...): " + clip(term, 120));
            }
            TemplatePrecheckResult affineResult = validateAffineBody(reluMatcher.group(1));
            if (!affineResult.isValid()) {
                return affineResult;
            }
        }
        return TemplatePrecheckResult.valid();
    }

    private TemplatePrecheckResult validateAffineBody(String body) {
        String compact = nonNull(body).replace("`", "").replaceAll("\\s+", "");
        compact = unwrapOuterParentheses(compact);
        if (compact.isBlank()) {
            return TemplatePrecheckResult.invalid("ReLU body is empty.");
        }

        if (Pattern.compile("(?i)relu\\s*\\(").matcher(compact).find()) {
            return TemplatePrecheckResult.invalid("nested ReLU is not allowed.");
        }
        if (Pattern.compile("(?i)\\b(min|max|abs|if|else|where|pow|sqrt|log|exp|sin|cos|tan)\\b").matcher(compact).find()) {
            return TemplatePrecheckResult.invalid("non-linear function/operator detected.");
        }
        if (Pattern.compile("(\\^|/|\\?|:|&&|\\|\\||<=|>=|==|!=|<|>|%)").matcher(compact).find()) {
            return TemplatePrecheckResult.invalid("unsupported operator detected.");
        }
        if (Pattern.compile("(?i)\\b[a-z_][a-z0-9_]*\\b\\*\\b[a-z_][a-z0-9_]*\\b").matcher(compact).find()) {
            return TemplatePrecheckResult.invalid("variable*variable is not allowed.");
        }
        if (!Pattern.compile("^[A-Za-z0-9_+\\-*.()]+$").matcher(compact).matches()) {
            return TemplatePrecheckResult.invalid("unsupported token detected.");
        }

        List<String> terms = splitSignedTerms(compact);
        if (terms.isEmpty()) {
            return TemplatePrecheckResult.invalid("no affine terms detected.");
        }
        for (String rawTerm : terms) {
            String term = unwrapOuterParentheses(nonNull(rawTerm).trim());
            if (term.isBlank()) {
                continue;
            }
            if (term.matches("^[+-]?\\d+(?:\\.\\d+)?$")) {
                continue;
            }
            if (term.matches("^[+-]?[A-Za-z_][A-Za-z0-9_]*$")) {
                continue;
            }
            if (term.matches("^[+-]?\\d+(?:\\.\\d+)?\\*[A-Za-z_][A-Za-z0-9_]*$")) {
                continue;
            }
            return TemplatePrecheckResult.invalid("non-affine term detected: " + clip(term, 80));
        }
        return TemplatePrecheckResult.valid();
    }

    private List<String> splitTopLevel(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return parts;
        }
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i += 1) {
            char ch = text.charAt(i);
            if (ch == '(') {
                depth += 1;
            } else if (ch == ')') {
                depth = Math.max(0, depth - 1);
            }
            if (ch == delimiter && depth == 0) {
                parts.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    private List<String> splitSignedTerms(String expression) {
        List<String> terms = new ArrayList<>();
        if (expression == null || expression.isBlank()) {
            return terms;
        }
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < expression.length(); i += 1) {
            char ch = expression.charAt(i);
            if (ch == '(') {
                depth += 1;
            } else if (ch == ')') {
                depth = Math.max(0, depth - 1);
            }
            if ((ch == '+' || ch == '-') && i > 0 && depth == 0) {
                terms.add(current.toString());
                current.setLength(0);
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            terms.add(current.toString());
        }
        return terms;
    }

    private String unwrapOuterParentheses(String text) {
        String normalized = nonNull(text).trim();
        while (normalized.length() >= 2 && normalized.startsWith("(") && normalized.endsWith(")")) {
            int depth = 0;
            boolean matchedAtEnd = false;
            for (int i = 0; i < normalized.length(); i += 1) {
                char ch = normalized.charAt(i);
                if (ch == '(') depth += 1;
                if (ch == ')') depth -= 1;
                if (depth == 0) {
                    matchedAtEnd = i == normalized.length() - 1;
                    break;
                }
            }
            if (!matchedAtEnd) {
                break;
            }
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
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

    private static final class TemplatePrecheckResult {
        private final boolean valid;
        private final String detail;

        private TemplatePrecheckResult(boolean valid, String detail) {
            this.valid = valid;
            this.detail = detail == null ? "" : detail.trim();
        }

        private static TemplatePrecheckResult valid() {
            return new TemplatePrecheckResult(true, "");
        }

        private static TemplatePrecheckResult invalid(String detail) {
            return new TemplatePrecheckResult(false, detail);
        }

        private boolean isValid() {
            return valid;
        }

        private String getDetail() {
            return detail;
        }
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

        Set<String> candidates = new LinkedHashSet<>();
        Matcher fenced = Pattern.compile("```(?:[a-zA-Z0-9_-]+)?\\s*([\\s\\S]*?)```").matcher(text);
        while (fenced.find()) {
            String fencedBody = nonNull(fenced.group(1)).trim();
            if (!fencedBody.isBlank()) {
                candidates.add(fencedBody);
            }
        }

        String vectorExpr = firstRegex(text, "\\[[^\\[\\]]*ReLU[^\\[\\]]*\\]");
        if (!vectorExpr.isBlank()) {
            candidates.add(vectorExpr.trim());
        }

        candidates.add(text);
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
            if (line.contains("ReLU(") || line.contains("relu(")
                    || line.contains("Max(") || line.contains("max(")
                    || isNoReluSolutionCandidate(line)) {
                candidates.add(line);
            }
        }

        String first = lines.length > 0 ? lines[0].trim() : text;
        if (first.matches("^[A-Za-z_][\\w\\s]*\\(?.*?\\)?\\s*=\\s*.+$")) {
            int eq = first.indexOf('=');
            first = first.substring(eq + 1).trim();
        }
        if (!first.isBlank()) {
            candidates.add(first);
        }

        for (String candidateExpr : candidates) {
            String normalized = nonNull(candidateExpr).trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (isNoReluSolutionCandidate(normalized)) {
                return normalized;
            }
            if (precheckAffineReluTemplate(normalized).isValid()) {
                return normalized;
            }
        }
        for (String candidateExpr : candidates) {
            String normalized = nonNull(candidateExpr).trim();
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
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
