package com.course.ideology.api;

import com.course.ideology.api.dto.SiliconFlowApiKeyRequest;
import com.course.ideology.api.dto.SiliconFlowApiKeyResponse;
import com.course.ideology.service.SiliconFlowSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/settings/qwen-key")
@Validated
public class QwenSettingsController {
    private static final Logger log = LoggerFactory.getLogger(QwenSettingsController.class);

    private final SiliconFlowSettingsService settingsService;

    public QwenSettingsController(SiliconFlowSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public SiliconFlowApiKeyResponse getKeyStatus() {
        SiliconFlowSettingsService.ApiKeyStatus status = settingsService.getQwenApiKeyStatus();
        log.info("Qwen key status queried: hasApiKey={}, masked={}, target={}",
                status.isHasApiKey(),
                status.getMaskedApiKey(),
                status.getServiceTarget());
        return toResponse(status);
    }

    @PutMapping
    public SiliconFlowApiKeyResponse saveApiKey(@Valid @RequestBody SiliconFlowApiKeyRequest request) {
        try {
            SiliconFlowSettingsService.ApiKeyStatus status = settingsService.saveQwenApiKey(request.getApiKey());
            log.info("Qwen key saved: hasApiKey={}, masked={}, updatedAt={}",
                    status.isHasApiKey(),
                    status.getMaskedApiKey(),
                    status.getUpdatedAt());
            return toResponse(status);
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
