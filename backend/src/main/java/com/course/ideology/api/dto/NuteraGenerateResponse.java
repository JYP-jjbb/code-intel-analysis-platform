package com.course.ideology.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class NuteraGenerateResponse {
    @JsonProperty("candidate_function")
    private String candidateFunction;

    @JsonProperty("raw_response")
    private String rawResponse;

    private String status;

    private String log;

    @JsonProperty("checker_feedback")
    private String checkerFeedback;

    @JsonProperty("checker_status")
    private String checkerStatus;

    @JsonProperty("checker_message")
    private String checkerMessage;

    @JsonProperty("checker_raw_output")
    private String checkerRawOutput;

    @JsonProperty("checker_debug_message")
    private String checkerDebugMessage;

    @JsonProperty("checker_trace_tag")
    private String checkerTraceTag;

    @JsonProperty("checker_verdict")
    private String checkerVerdict;

    @JsonProperty("checker_conclusion")
    private String checkerConclusion;

    @JsonProperty("checker_counterexample")
    private String checkerCounterexample;

    @JsonProperty("final_summary")
    private String finalSummary;

    private String message;

    @JsonProperty("batch_mode")
    private boolean batchMode;

    @JsonProperty("batch_total")
    private int batchTotal;

    @JsonProperty("batch_completed")
    private int batchCompleted;

    @JsonProperty("batch_current_case")
    private String batchCurrentCase;

    @JsonProperty("batch_proved")
    private int batchProved;

    @JsonProperty("batch_not_proved")
    private int batchNotProved;

    @JsonProperty("batch_error")
    private int batchError;

    @JsonProperty("batch_stop")
    private int batchStop;

    @JsonProperty("batch_result_path")
    private String batchResultPath;

    @JsonProperty("batch_results")
    private List<BatchCaseResult> batchResults = new ArrayList<>();

    public NuteraGenerateResponse() {
    }

    public String getCandidateFunction() {
        return candidateFunction;
    }

    public void setCandidateFunction(String candidateFunction) {
        this.candidateFunction = candidateFunction;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public String getCheckerFeedback() {
        return checkerFeedback;
    }

    public void setCheckerFeedback(String checkerFeedback) {
        this.checkerFeedback = checkerFeedback;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCheckerStatus() {
        return checkerStatus;
    }

    public void setCheckerStatus(String checkerStatus) {
        this.checkerStatus = checkerStatus;
    }

    public String getCheckerMessage() {
        return checkerMessage;
    }

    public void setCheckerMessage(String checkerMessage) {
        this.checkerMessage = checkerMessage;
    }

    public String getCheckerRawOutput() {
        return checkerRawOutput;
    }

    public void setCheckerRawOutput(String checkerRawOutput) {
        this.checkerRawOutput = checkerRawOutput;
    }

    public String getCheckerDebugMessage() {
        return checkerDebugMessage;
    }

    public void setCheckerDebugMessage(String checkerDebugMessage) {
        this.checkerDebugMessage = checkerDebugMessage;
    }

    public String getCheckerTraceTag() {
        return checkerTraceTag;
    }

    public void setCheckerTraceTag(String checkerTraceTag) {
        this.checkerTraceTag = checkerTraceTag;
    }

    public String getFinalSummary() {
        return finalSummary;
    }

    public void setFinalSummary(String finalSummary) {
        this.finalSummary = finalSummary;
    }

    public String getCheckerConclusion() {
        return checkerConclusion;
    }

    public void setCheckerConclusion(String checkerConclusion) {
        this.checkerConclusion = checkerConclusion;
    }

    public String getCheckerVerdict() {
        return checkerVerdict;
    }

    public void setCheckerVerdict(String checkerVerdict) {
        this.checkerVerdict = checkerVerdict;
    }

    public String getCheckerCounterexample() {
        return checkerCounterexample;
    }

    public void setCheckerCounterexample(String checkerCounterexample) {
        this.checkerCounterexample = checkerCounterexample;
    }

    public boolean isBatchMode() {
        return batchMode;
    }

    public void setBatchMode(boolean batchMode) {
        this.batchMode = batchMode;
    }

    public int getBatchTotal() {
        return batchTotal;
    }

    public void setBatchTotal(int batchTotal) {
        this.batchTotal = batchTotal;
    }

    public int getBatchCompleted() {
        return batchCompleted;
    }

    public void setBatchCompleted(int batchCompleted) {
        this.batchCompleted = batchCompleted;
    }

    public String getBatchCurrentCase() {
        return batchCurrentCase;
    }

    public void setBatchCurrentCase(String batchCurrentCase) {
        this.batchCurrentCase = batchCurrentCase;
    }

    public int getBatchProved() {
        return batchProved;
    }

    public void setBatchProved(int batchProved) {
        this.batchProved = batchProved;
    }

    public int getBatchNotProved() {
        return batchNotProved;
    }

    public void setBatchNotProved(int batchNotProved) {
        this.batchNotProved = batchNotProved;
    }

    public int getBatchError() {
        return batchError;
    }

    public void setBatchError(int batchError) {
        this.batchError = batchError;
    }

    public int getBatchStop() {
        return batchStop;
    }

    public void setBatchStop(int batchStop) {
        this.batchStop = batchStop;
    }

    public String getBatchResultPath() {
        return batchResultPath;
    }

    public void setBatchResultPath(String batchResultPath) {
        this.batchResultPath = batchResultPath;
    }

    public List<BatchCaseResult> getBatchResults() {
        return batchResults;
    }

    public void setBatchResults(List<BatchCaseResult> batchResults) {
        this.batchResults = batchResults == null ? new ArrayList<>() : batchResults;
    }

    public static class BatchCaseResult {
        private String caseName;

        @JsonProperty("candidate_function")
        private String candidateFunction;

        @JsonProperty("execution_status")
        private String executionStatus;

        @JsonProperty("verification_status")
        private String verificationStatus;

        private String conclusion;
        private String message;
        private String counterexample;

        @JsonProperty("attempt_count")
        private int attemptCount;

        @JsonProperty("final_status")
        private String finalStatus;

        @JsonProperty("stop_reason")
        private String stopReason;

        @JsonProperty("attempt_states")
        private List<String> attemptStates = new ArrayList<>();

        @JsonProperty("checker_raw_output")
        private String checkerRawOutput;

        @JsonProperty("checker_feedback")
        private String checkerFeedback;

        @JsonProperty("debug_message")
        private String debugMessage;

        @JsonProperty("trace_tag")
        private String traceTag;

        private String log;

        public String getCaseName() {
            return caseName;
        }

        public void setCaseName(String caseName) {
            this.caseName = caseName;
        }

        public String getCandidateFunction() {
            return candidateFunction;
        }

        public void setCandidateFunction(String candidateFunction) {
            this.candidateFunction = candidateFunction;
        }

        public String getExecutionStatus() {
            return executionStatus;
        }

        public void setExecutionStatus(String executionStatus) {
            this.executionStatus = executionStatus;
        }

        public String getVerificationStatus() {
            return verificationStatus;
        }

        public void setVerificationStatus(String verificationStatus) {
            this.verificationStatus = verificationStatus;
        }

        public String getConclusion() {
            return conclusion;
        }

        public void setConclusion(String conclusion) {
            this.conclusion = conclusion;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getCounterexample() {
            return counterexample;
        }

        public void setCounterexample(String counterexample) {
            this.counterexample = counterexample;
        }

        public String getCheckerRawOutput() {
            return checkerRawOutput;
        }

        public void setCheckerRawOutput(String checkerRawOutput) {
            this.checkerRawOutput = checkerRawOutput;
        }

        public String getCheckerFeedback() {
            return checkerFeedback;
        }

        public void setCheckerFeedback(String checkerFeedback) {
            this.checkerFeedback = checkerFeedback;
        }

        public String getDebugMessage() {
            return debugMessage;
        }

        public void setDebugMessage(String debugMessage) {
            this.debugMessage = debugMessage;
        }

        public String getTraceTag() {
            return traceTag;
        }

        public void setTraceTag(String traceTag) {
            this.traceTag = traceTag;
        }

        public int getAttemptCount() {
            return attemptCount;
        }

        public void setAttemptCount(int attemptCount) {
            this.attemptCount = attemptCount;
        }

        public String getFinalStatus() {
            return finalStatus;
        }

        public void setFinalStatus(String finalStatus) {
            this.finalStatus = finalStatus;
        }

        public String getStopReason() {
            return stopReason;
        }

        public void setStopReason(String stopReason) {
            this.stopReason = stopReason;
        }

        public List<String> getAttemptStates() {
            return attemptStates;
        }

        public void setAttemptStates(List<String> attemptStates) {
            this.attemptStates = attemptStates == null ? new ArrayList<>() : new ArrayList<>(attemptStates);
        }

        public String getLog() {
            return log;
        }

        public void setLog(String log) {
            this.log = log;
        }
    }
}
