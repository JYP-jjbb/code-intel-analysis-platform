package com.course.ideology.api.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class CodeRunTaskRequest {
    @NotBlank(message = "language is required")
    private String language;

    @NotBlank(message = "sourceCode is required")
    @Size(max = 200000, message = "sourceCode is too long")
    @JsonAlias({"code", "source_code"})
    private String sourceCode;

    @Size(max = 200000, message = "stdin is too long")
    @JsonAlias({"stdinText", "stdin_text"})
    private String stdin;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getStdin() {
        return stdin;
    }

    public void setStdin(String stdin) {
        this.stdin = stdin;
    }
}
