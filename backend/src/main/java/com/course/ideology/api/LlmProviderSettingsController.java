package com.course.ideology.api;

import com.course.ideology.api.dto.LlmProviderSettingsRequest;
import com.course.ideology.api.dto.LlmProviderSettingsResponse;
import com.course.ideology.service.LlmProviderSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/settings/llm/providers")
@Validated
public class LlmProviderSettingsController {
    private static final Logger log = LoggerFactory.getLogger(LlmProviderSettingsController.class);

    private final LlmProviderSettingsService providerSettingsService;

    public LlmProviderSettingsController(LlmProviderSettingsService providerSettingsService) {
        this.providerSettingsService = providerSettingsService;
    }

    @GetMapping("/{providerId}")
    public LlmProviderSettingsResponse getProviderSettings(@PathVariable String providerId) {
        try {
            LlmProviderSettingsService.ProviderSettings settings = providerSettingsService.getProviderSettings(providerId);
            log.info("LLM provider settings queried: provider={}, hasApiKey={}, model={}, baseUrl={}",
                    settings.getProviderId(),
                    settings.isHasApiKey(),
                    settings.getModelName(),
                    settings.getBaseUrl());
            return toResponse(settings);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PutMapping("/{providerId}")
    public LlmProviderSettingsResponse saveProviderSettings(@PathVariable String providerId,
                                                            @RequestBody LlmProviderSettingsRequest request) {
        try {
            LlmProviderSettingsService.ProviderSettingsUpdate update = new LlmProviderSettingsService.ProviderSettingsUpdate();
            update.setProviderName(request.getProviderName());
            update.setModelName(request.getModelName());
            update.setApiKey(request.getApiKey());
            update.setBaseUrl(request.getBaseUrl());
            update.setEnableThinking(request.getEnableThinking());
            update.setTemperature(request.getTemperature());
            update.setMaxTokens(request.getMaxTokens());
            update.setStream(request.getStream());
            update.setTimeout(request.getTimeout());
            update.setExtraConfig(request.getExtraConfig());

            LlmProviderSettingsService.ProviderSettings settings = providerSettingsService.saveProviderSettings(providerId, update);
            log.info("LLM provider settings saved: provider={}, hasApiKey={}, model={}, baseUrl={}, enableThinking={}",
                    settings.getProviderId(),
                    settings.isHasApiKey(),
                    settings.getModelName(),
                    settings.getBaseUrl(),
                    settings.getEnableThinking());
            return toResponse(settings);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private LlmProviderSettingsResponse toResponse(LlmProviderSettingsService.ProviderSettings settings) {
        LlmProviderSettingsResponse response = new LlmProviderSettingsResponse();
        response.setProviderId(settings.getProviderId());
        response.setProviderName(settings.getProviderName());
        response.setModelName(settings.getModelName());
        response.setBaseUrl(settings.getBaseUrl());
        response.setEnableThinking(settings.getEnableThinking());
        response.setTemperature(settings.getTemperature());
        response.setMaxTokens(settings.getMaxTokens());
        response.setStream(settings.getStream());
        response.setTimeout(settings.getTimeout());
        response.setExtraConfig(settings.getExtraConfig());
        response.setHasApiKey(settings.isHasApiKey());
        response.setMaskedApiKey(settings.getMaskedApiKey());
        response.setApiKeyUpdatedAt(settings.getApiKeyUpdatedAt());
        response.setUpdatedAt(settings.getUpdatedAt());
        response.setServiceTarget(settings.getServiceTarget());
        return response;
    }
}
