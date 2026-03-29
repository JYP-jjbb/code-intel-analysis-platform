package com.course.ideology.api;

import com.course.ideology.api.dto.SiliconFlowApiKeyRequest;
import com.course.ideology.api.dto.SiliconFlowApiKeyResponse;
import com.course.ideology.service.SiliconFlowSettingsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/settings/siliconflow-key")
@Validated
public class SiliconFlowSettingsController {
    private final SiliconFlowSettingsService settingsService;

    public SiliconFlowSettingsController(SiliconFlowSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public SiliconFlowApiKeyResponse getKeyStatus() {
        return toResponse(settingsService.getApiKeyStatus());
    }

    @PutMapping
    public SiliconFlowApiKeyResponse saveApiKey(@Valid @RequestBody SiliconFlowApiKeyRequest request) {
        try {
            return toResponse(settingsService.saveApiKey(request.getApiKey()));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private SiliconFlowApiKeyResponse toResponse(SiliconFlowSettingsService.ApiKeyStatus status) {
        return new SiliconFlowApiKeyResponse(
                status.isHasApiKey(),
                status.getMaskedApiKey(),
                status.getUpdatedAt(),
                status.getServiceTarget()
        );
    }
}
