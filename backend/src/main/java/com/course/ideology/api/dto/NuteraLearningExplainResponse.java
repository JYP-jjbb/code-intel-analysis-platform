package com.course.ideology.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class NuteraLearningExplainResponse {
    private String status;
    private String message;

    @JsonProperty("line_explanations")
    private List<LineExplanation> lineExplanations = new ArrayList<>();

    @JsonProperty("code_blocks")
    private List<CodeBlockExplanation> codeBlocks = new ArrayList<>();

    @JsonProperty("selected_block")
    private CodeBlockExplanation selectedBlock;

    @JsonProperty("raw_response")
    private String rawResponse;

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

    public List<LineExplanation> getLineExplanations() {
        return lineExplanations;
    }

    public void setLineExplanations(List<LineExplanation> lineExplanations) {
        this.lineExplanations = lineExplanations == null ? new ArrayList<>() : lineExplanations;
    }

    public List<CodeBlockExplanation> getCodeBlocks() {
        return codeBlocks;
    }

    public void setCodeBlocks(List<CodeBlockExplanation> codeBlocks) {
        this.codeBlocks = codeBlocks == null ? new ArrayList<>() : codeBlocks;
    }

    public CodeBlockExplanation getSelectedBlock() {
        return selectedBlock;
    }

    public void setSelectedBlock(CodeBlockExplanation selectedBlock) {
        this.selectedBlock = selectedBlock;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    public static class LineExplanation {
        @JsonProperty("line_number")
        private int lineNumber;

        @JsonProperty("line_text")
        private String lineText;

        @JsonProperty("line_explanation")
        private String lineExplanation;

        @JsonProperty("syntax_point")
        private String syntaxPoint;

        @JsonProperty("common_mistake")
        private String commonMistake;

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }

        public String getLineText() {
            return lineText;
        }

        public void setLineText(String lineText) {
            this.lineText = lineText;
        }

        public String getLineExplanation() {
            return lineExplanation;
        }

        public void setLineExplanation(String lineExplanation) {
            this.lineExplanation = lineExplanation;
        }

        public String getSyntaxPoint() {
            return syntaxPoint;
        }

        public void setSyntaxPoint(String syntaxPoint) {
            this.syntaxPoint = syntaxPoint;
        }

        public String getCommonMistake() {
            return commonMistake;
        }

        public void setCommonMistake(String commonMistake) {
            this.commonMistake = commonMistake;
        }
    }

    public static class CodeBlockExplanation {
        @JsonProperty("start_line")
        private int startLine;

        @JsonProperty("end_line")
        private int endLine;

        @JsonProperty("block_title")
        private String blockTitle;

        @JsonProperty("block_type")
        private String blockType;

        @JsonProperty("block_explanation")
        private String blockExplanation;

        @JsonProperty("key_points")
        private List<String> keyPoints = new ArrayList<>();

        @JsonProperty("common_mistakes")
        private List<String> commonMistakes = new ArrayList<>();

        public int getStartLine() {
            return startLine;
        }

        public void setStartLine(int startLine) {
            this.startLine = startLine;
        }

        public int getEndLine() {
            return endLine;
        }

        public void setEndLine(int endLine) {
            this.endLine = endLine;
        }

        public String getBlockTitle() {
            return blockTitle;
        }

        public void setBlockTitle(String blockTitle) {
            this.blockTitle = blockTitle;
        }

        public String getBlockType() {
            return blockType;
        }

        public void setBlockType(String blockType) {
            this.blockType = blockType;
        }

        public String getBlockExplanation() {
            return blockExplanation;
        }

        public void setBlockExplanation(String blockExplanation) {
            this.blockExplanation = blockExplanation;
        }

        public List<String> getKeyPoints() {
            return keyPoints;
        }

        public void setKeyPoints(List<String> keyPoints) {
            this.keyPoints = keyPoints == null ? new ArrayList<>() : keyPoints;
        }

        public List<String> getCommonMistakes() {
            return commonMistakes;
        }

        public void setCommonMistakes(List<String> commonMistakes) {
            this.commonMistakes = commonMistakes == null ? new ArrayList<>() : commonMistakes;
        }
    }
}

