package com.course.ideology.api.dto;

import java.util.ArrayList;
import java.util.List;

public class CodeReviewProjectErrorResponse {
    private String message;
    private String detail;
    private String errorCode;
    private List<String> hints = new ArrayList<>();

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public List<String> getHints() {
        return hints;
    }

    public void setHints(List<String> hints) {
        this.hints = hints == null ? new ArrayList<>() : new ArrayList<>(hints);
    }
}
