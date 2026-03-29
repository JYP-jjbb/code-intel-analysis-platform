package com.course.ideology.api.dto;

import javax.validation.constraints.NotBlank;

public class SiliconFlowApiKeyRequest {
    @NotBlank
    private String apiKey;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
