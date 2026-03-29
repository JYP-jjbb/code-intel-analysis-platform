package com.course.ideology.api.dto;

public class SiliconFlowApiKeyResponse {
    private boolean hasApiKey;
    private String maskedApiKey;
    private String updatedAt;
    private String serviceTarget;

    public SiliconFlowApiKeyResponse() {
    }

    public SiliconFlowApiKeyResponse(boolean hasApiKey, String maskedApiKey, String updatedAt, String serviceTarget) {
        this.hasApiKey = hasApiKey;
        this.maskedApiKey = maskedApiKey;
        this.updatedAt = updatedAt;
        this.serviceTarget = serviceTarget;
    }

    public boolean isHasApiKey() {
        return hasApiKey;
    }

    public void setHasApiKey(boolean hasApiKey) {
        this.hasApiKey = hasApiKey;
    }

    public String getMaskedApiKey() {
        return maskedApiKey;
    }

    public void setMaskedApiKey(String maskedApiKey) {
        this.maskedApiKey = maskedApiKey;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getServiceTarget() {
        return serviceTarget;
    }

    public void setServiceTarget(String serviceTarget) {
        this.serviceTarget = serviceTarget;
    }
}
