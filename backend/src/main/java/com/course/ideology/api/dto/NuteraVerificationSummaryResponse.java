package com.course.ideology.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class NuteraVerificationSummaryResponse {
    private String status;
    private String message;

    @JsonProperty("summary_text")
    private String summaryText;

    @JsonProperty("verification_status")
    private String verificationStatus;

    @JsonProperty("candidate_function")
    private String candidateFunction;

    @JsonProperty("focus_lines")
    private List<Integer> focusLines = new ArrayList<>();

    private GraphData graph = new GraphData();
    private SliceData slice = new SliceData();
    private VerificationInsight insight = new VerificationInsight();

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

    public String getSummaryText() {
        return summaryText;
    }

    public void setSummaryText(String summaryText) {
        this.summaryText = summaryText;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getCandidateFunction() {
        return candidateFunction;
    }

    public void setCandidateFunction(String candidateFunction) {
        this.candidateFunction = candidateFunction;
    }

    public List<Integer> getFocusLines() {
        return focusLines;
    }

    public void setFocusLines(List<Integer> focusLines) {
        this.focusLines = focusLines == null ? new ArrayList<>() : new ArrayList<>(focusLines);
    }

    public GraphData getGraph() {
        return graph;
    }

    public void setGraph(GraphData graph) {
        this.graph = graph == null ? new GraphData() : graph;
    }

    public SliceData getSlice() {
        return slice;
    }

    public void setSlice(SliceData slice) {
        this.slice = slice == null ? new SliceData() : slice;
    }

    public VerificationInsight getInsight() {
        return insight;
    }

    public void setInsight(VerificationInsight insight) {
        this.insight = insight == null ? new VerificationInsight() : insight;
    }

    public static class GraphData {
        private List<GraphNode> nodes = new ArrayList<>();
        private List<GraphEdge> edges = new ArrayList<>();

        public List<GraphNode> getNodes() {
            return nodes;
        }

        public void setNodes(List<GraphNode> nodes) {
            this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
        }

        public List<GraphEdge> getEdges() {
            return edges;
        }

        public void setEdges(List<GraphEdge> edges) {
            this.edges = edges == null ? new ArrayList<>() : new ArrayList<>(edges);
        }
    }

    public static class GraphNode {
        private String id;
        private String label;
        private String type;

        @JsonProperty("line_start")
        private int lineStart;

        @JsonProperty("line_end")
        private int lineEnd;

        private String status;
        private String explanation;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getLineStart() {
            return lineStart;
        }

        public void setLineStart(int lineStart) {
            this.lineStart = lineStart;
        }

        public int getLineEnd() {
            return lineEnd;
        }

        public void setLineEnd(int lineEnd) {
            this.lineEnd = lineEnd;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getExplanation() {
            return explanation;
        }

        public void setExplanation(String explanation) {
            this.explanation = explanation;
        }
    }

    public static class GraphEdge {
        private String source;
        private String target;
        private String type;
        private String status;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    public static class SliceData {
        private List<Integer> lines = new ArrayList<>();
        private String code;

        public List<Integer> getLines() {
            return lines;
        }

        public void setLines(List<Integer> lines) {
            this.lines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    public static class VerificationInsight {
        private String target;

        @JsonProperty("checker_conclusion")
        private String checkerConclusion;

        @JsonProperty("proof_outcome")
        private String proofOutcome;

        @JsonProperty("failure_reason")
        private String failureReason;

        @JsonProperty("highlight_explanation")
        private String highlightExplanation;

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public String getCheckerConclusion() {
            return checkerConclusion;
        }

        public void setCheckerConclusion(String checkerConclusion) {
            this.checkerConclusion = checkerConclusion;
        }

        public String getProofOutcome() {
            return proofOutcome;
        }

        public void setProofOutcome(String proofOutcome) {
            this.proofOutcome = proofOutcome;
        }

        public String getFailureReason() {
            return failureReason;
        }

        public void setFailureReason(String failureReason) {
            this.failureReason = failureReason;
        }

        public String getHighlightExplanation() {
            return highlightExplanation;
        }

        public void setHighlightExplanation(String highlightExplanation) {
            this.highlightExplanation = highlightExplanation;
        }
    }
}

