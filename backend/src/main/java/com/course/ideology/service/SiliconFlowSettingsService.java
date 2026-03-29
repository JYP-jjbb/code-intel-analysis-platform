package com.course.ideology.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class SiliconFlowSettingsService {
    private static final String API_KEY_FIELD = "siliconflowApiKey";
    private static final String API_KEY_UPDATED_AT_FIELD = "siliconflowApiKeyUpdatedAt";
    private static final String LEGACY_API_KEY_UPDATED_AT_FIELD = "apiKeyUpdatedAt";
    private static final String SERVICE_TARGET = "SiliconFlow Chat Completions";

    private final ObjectMapper objectMapper;
    private final Path settingsPath;

    public SiliconFlowSettingsService(ObjectMapper objectMapper,
                                      @Value("${app.settings.file:workspace/settings/settings.json}") String configuredPath) {
        this.objectMapper = objectMapper;
        this.settingsPath = resolveSettingsPath(configuredPath);
    }

    public synchronized ApiKeyStatus saveApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("SiliconFlow API Key cannot be empty.");
        }
        try {
            Path parent = settingsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> settings = readSettings();
            String trimmedKey = apiKey.trim();
            String now = Instant.now().toString();
            settings.put(API_KEY_FIELD, trimmedKey);
            settings.put(API_KEY_UPDATED_AT_FIELD, now);
            settings.put(LEGACY_API_KEY_UPDATED_AT_FIELD, now);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsPath.toFile(), settings);
            return buildStatus(settings);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save SiliconFlow API Key: " + ex.getMessage(), ex);
        }
    }

    public synchronized ApiKeyStatus getApiKeyStatus() {
        try {
            return buildStatus(readSettings());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read API key status: " + ex.getMessage(), ex);
        }
    }

    public synchronized String getApiKey() {
        return getApiKeyStatus().getApiKey();
    }

    public synchronized boolean hasApiKey() {
        return getApiKeyStatus().isHasApiKey();
    }

    public synchronized String getMaskedApiKey() {
        return getApiKeyStatus().getMaskedApiKey();
    }

    public synchronized String getApiKeyUpdatedAt() {
        return getApiKeyStatus().getUpdatedAt();
    }

    public String getServiceTarget() {
        return SERVICE_TARGET;
    }

    public static class ApiKeyStatus {
        private final String apiKey;
        private final boolean hasApiKey;
        private final String maskedApiKey;
        private final String updatedAt;
        private final String serviceTarget;

        public ApiKeyStatus(String apiKey, boolean hasApiKey, String maskedApiKey, String updatedAt, String serviceTarget) {
            this.apiKey = apiKey;
            this.hasApiKey = hasApiKey;
            this.maskedApiKey = maskedApiKey;
            this.updatedAt = updatedAt;
            this.serviceTarget = serviceTarget;
        }

        public String getApiKey() {
            return apiKey;
        }

        public boolean isHasApiKey() {
            return hasApiKey;
        }

        public String getMaskedApiKey() {
            return maskedApiKey;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public String getServiceTarget() {
            return serviceTarget;
        }
    }

    private Map<String, Object> readSettings() throws Exception {
        if (!Files.exists(settingsPath)) {
            return new HashMap<>();
        }
        Map<String, Object> map = objectMapper.readValue(settingsPath.toFile(), new TypeReference<Map<String, Object>>() {
        });
        if (map == null) {
            return new HashMap<>();
        }
        return new HashMap<>(map);
    }

    private ApiKeyStatus buildStatus(Map<String, Object> settings) throws Exception {
        String apiKey = readString(settings.get(API_KEY_FIELD));
        boolean hasApiKey = !apiKey.isBlank();
        String updatedAt = resolveUpdatedAt(settings, hasApiKey);
        return new ApiKeyStatus(apiKey, hasApiKey, maskApiKey(apiKey), updatedAt, SERVICE_TARGET);
    }

    private String resolveUpdatedAt(Map<String, Object> settings, boolean hasApiKey) throws Exception {
        String updatedAt = readString(settings.get(API_KEY_UPDATED_AT_FIELD));
        if (!updatedAt.isBlank()) {
            return updatedAt;
        }

        updatedAt = readString(settings.get(LEGACY_API_KEY_UPDATED_AT_FIELD));
        if (!updatedAt.isBlank()) {
            return updatedAt;
        }

        if (hasApiKey && Files.exists(settingsPath)) {
            FileTime fileTime = Files.getLastModifiedTime(settingsPath);
            return fileTime.toInstant().toString();
        }
        return "";
    }

    private String readString(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private String maskApiKey(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private Path resolveSettingsPath(String configuredPath) {
        Path candidate = Path.of(configuredPath);
        if (!candidate.isAbsolute()) {
            Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
            return current.resolve(candidate).normalize();
        }
        return candidate.toAbsolutePath().normalize();
    }
}
