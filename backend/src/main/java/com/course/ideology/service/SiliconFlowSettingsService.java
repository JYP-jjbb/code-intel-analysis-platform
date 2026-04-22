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
    private static final String MOONSHOT_API_KEY_FIELD = "moonshotApiKey";
    private static final String MOONSHOT_API_KEY_UPDATED_AT_FIELD = "moonshotApiKeyUpdatedAt";
    private static final String HUNYUAN_API_KEY_FIELD = "hunyuanApiKey";
    private static final String HUNYUAN_API_KEY_UPDATED_AT_FIELD = "hunyuanApiKeyUpdatedAt";
    private static final String QWEN_API_KEY_FIELD = "qwenApiKey";
    private static final String QWEN_API_KEY_UPDATED_AT_FIELD = "qwenApiKeyUpdatedAt";
    private static final String LEGACY_API_KEY_UPDATED_AT_FIELD = "apiKeyUpdatedAt";
    private static final String DEEPSEEK_API_KEY_FIELD = "deepseekApiKey";
    private static final String DEEPSEEK_API_KEY_UPDATED_AT_FIELD = "deepseekApiKeyUpdatedAt";
    private static final String MOONSHOT_SERVICE_TARGET = "Moonshot Official Chat Completions";
    private static final String HUNYUAN_SERVICE_TARGET = "Tencent Hunyuan Official Chat Completions";
    private static final String DEEPSEEK_SERVICE_TARGET = "DeepSeek Official Chat/Reasoner (Scenario Routed)";
    private static final String QWEN_SERVICE_TARGET = "Qwen Official (DashScope Compatible) Chat Completions";

    private final ObjectMapper objectMapper;
    private final Path settingsPath;

    public SiliconFlowSettingsService(ObjectMapper objectMapper,
                                      @Value("${app.settings.file:workspace/settings/settings.json}") String configuredPath) {
        this.objectMapper = objectMapper;
        this.settingsPath = resolveSettingsPath(configuredPath);
    }

    public synchronized ApiKeyStatus saveApiKey(String apiKey) {
        return saveMoonshotApiKey(apiKey);
    }

    public synchronized ApiKeyStatus saveMoonshotApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Moonshot API key cannot be empty.");
        }
        try {
            Path parent = settingsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> settings = readSettings();
            String trimmedKey = apiKey.trim();
            String now = Instant.now().toString();
            settings.put(MOONSHOT_API_KEY_FIELD, trimmedKey);
            settings.put(MOONSHOT_API_KEY_UPDATED_AT_FIELD, now);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsPath.toFile(), settings);
            return buildMoonshotStatus(settings);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save Moonshot API key: " + ex.getMessage(), ex);
        }
    }

    public synchronized ApiKeyStatus getApiKeyStatus() {
        return getMoonshotApiKeyStatus();
    }

    public synchronized ApiKeyStatus getMoonshotApiKeyStatus() {
        try {
            return buildMoonshotStatus(readSettings());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read Moonshot API key status: " + ex.getMessage(), ex);
        }
    }

    public synchronized ApiKeyStatus saveHunyuanApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Tencent Hunyuan API key cannot be empty.");
        }
        try {
            Path parent = settingsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> settings = readSettings();
            String trimmedKey = apiKey.trim();
            String now = Instant.now().toString();
            settings.put(HUNYUAN_API_KEY_FIELD, trimmedKey);
            settings.put(HUNYUAN_API_KEY_UPDATED_AT_FIELD, now);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsPath.toFile(), settings);
            return buildStatus(settings, HUNYUAN_API_KEY_FIELD, HUNYUAN_API_KEY_UPDATED_AT_FIELD, HUNYUAN_SERVICE_TARGET, false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save Tencent Hunyuan API key: " + ex.getMessage(), ex);
        }
    }

    public synchronized ApiKeyStatus getHunyuanApiKeyStatus() {
        try {
            return buildStatus(readSettings(), HUNYUAN_API_KEY_FIELD, HUNYUAN_API_KEY_UPDATED_AT_FIELD, HUNYUAN_SERVICE_TARGET, false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read Tencent Hunyuan API key status: " + ex.getMessage(), ex);
        }
    }

    public synchronized ApiKeyStatus saveDeepSeekApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("DeepSeek API key cannot be empty.");
        }
        try {
            Path parent = settingsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> settings = readSettings();
            String trimmedKey = apiKey.trim();
            String now = Instant.now().toString();
            settings.put(DEEPSEEK_API_KEY_FIELD, trimmedKey);
            settings.put(DEEPSEEK_API_KEY_UPDATED_AT_FIELD, now);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsPath.toFile(), settings);
            return buildStatus(settings, DEEPSEEK_API_KEY_FIELD, DEEPSEEK_API_KEY_UPDATED_AT_FIELD, DEEPSEEK_SERVICE_TARGET, false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save DeepSeek API key: " + ex.getMessage(), ex);
        }
    }

    public synchronized ApiKeyStatus getDeepSeekApiKeyStatus() {
        try {
            return buildStatus(readSettings(), DEEPSEEK_API_KEY_FIELD, DEEPSEEK_API_KEY_UPDATED_AT_FIELD, DEEPSEEK_SERVICE_TARGET, false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read DeepSeek API key status: " + ex.getMessage(), ex);
        }
    }

    public synchronized ApiKeyStatus saveQwenApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Qwen API key cannot be empty.");
        }
        try {
            Path parent = settingsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> settings = readSettings();
            String trimmedKey = apiKey.trim();
            String now = Instant.now().toString();
            settings.put(QWEN_API_KEY_FIELD, trimmedKey);
            settings.put(QWEN_API_KEY_UPDATED_AT_FIELD, now);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsPath.toFile(), settings);
            return buildStatus(settings, QWEN_API_KEY_FIELD, QWEN_API_KEY_UPDATED_AT_FIELD, QWEN_SERVICE_TARGET, false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save Qwen API key: " + ex.getMessage(), ex);
        }
    }

    public synchronized ApiKeyStatus getQwenApiKeyStatus() {
        try {
            return buildStatus(readSettings(), QWEN_API_KEY_FIELD, QWEN_API_KEY_UPDATED_AT_FIELD, QWEN_SERVICE_TARGET, false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read Qwen API key status: " + ex.getMessage(), ex);
        }
    }

    public synchronized String getApiKey() {
        return resolveMoonshotApiKey().getApiKey();
    }

    public synchronized String getMoonshotApiKey() {
        return resolveMoonshotApiKey().getApiKey();
    }

    public synchronized String getDeepSeekApiKey() {
        return resolveDeepSeekApiKey().getApiKey();
    }

    public synchronized String getQwenApiKey() {
        return resolveQwenApiKey().getApiKey();
    }

    public synchronized ResolvedApiKey resolveHunyuanApiKey() {
        try {
            Map<String, Object> settings = readSettings();
            String key = readString(settings.get(HUNYUAN_API_KEY_FIELD));
            if (!key.isBlank()) {
                return new ResolvedApiKey(key, "settings.hunyuanApiKey", true);
            }
            return new ResolvedApiKey("", "settings.missing", false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve Hunyuan API key: " + ex.getMessage(), ex);
        }
    }

    public synchronized ResolvedApiKey resolveMoonshotApiKey() {
        try {
            Map<String, Object> settings = readSettings();
            String key = readString(settings.get(MOONSHOT_API_KEY_FIELD));
            if (!key.isBlank()) {
                return new ResolvedApiKey(key, "settings.moonshotApiKey", true);
            }
            return new ResolvedApiKey("", "settings.missing", false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve Moonshot API key: " + ex.getMessage(), ex);
        }
    }

    public synchronized ResolvedApiKey resolveDeepSeekApiKey() {
        try {
            Map<String, Object> settings = readSettings();
            String deepSeekKey = readString(settings.get(DEEPSEEK_API_KEY_FIELD));
            if (!deepSeekKey.isBlank()) {
                return new ResolvedApiKey(deepSeekKey, "settings.deepseekApiKey", true);
            }
            return new ResolvedApiKey("", "settings.missing", false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve DeepSeek API key: " + ex.getMessage(), ex);
        }
    }

    public synchronized ResolvedApiKey resolveQwenApiKey() {
        try {
            Map<String, Object> settings = readSettings();
            String key = readString(settings.get(QWEN_API_KEY_FIELD));
            if (!key.isBlank()) {
                return new ResolvedApiKey(key, "settings.qwenApiKey", true);
            }
            return new ResolvedApiKey("", "settings.missing", false);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to resolve Qwen API key: " + ex.getMessage(), ex);
        }
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
        return MOONSHOT_SERVICE_TARGET;
    }

    public static class ResolvedApiKey {
        private final String apiKey;
        private final String source;
        private final boolean fromSettings;

        public ResolvedApiKey(String apiKey, String source, boolean fromSettings) {
            this.apiKey = apiKey;
            this.source = source;
            this.fromSettings = fromSettings;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getSource() {
            return source;
        }

        public boolean isFromSettings() {
            return fromSettings;
        }
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
        if (Files.size(settingsPath) == 0L) {
            return new HashMap<>();
        }
        Map<String, Object> map = objectMapper.readValue(settingsPath.toFile(), new TypeReference<Map<String, Object>>() {
        });
        if (map == null) {
            return new HashMap<>();
        }
        return new HashMap<>(map);
    }

    private ApiKeyStatus buildStatus(Map<String, Object> settings,
                                     String keyField,
                                     String updatedAtField,
                                     String serviceTarget,
                                     boolean fallbackLegacyUpdatedAtField) throws Exception {
        String apiKey = readString(settings.get(keyField));
        boolean hasApiKey = !apiKey.isBlank();
        String updatedAt = resolveUpdatedAt(settings, updatedAtField, hasApiKey, fallbackLegacyUpdatedAtField);
        return new ApiKeyStatus(apiKey, hasApiKey, maskApiKey(apiKey), updatedAt, serviceTarget);
    }

    private ApiKeyStatus buildMoonshotStatus(Map<String, Object> settings) throws Exception {
        String apiKey = readString(settings.get(MOONSHOT_API_KEY_FIELD));
        boolean hasApiKey = !apiKey.isBlank();
        String updatedAt = resolveMoonshotUpdatedAt(settings, hasApiKey);
        return new ApiKeyStatus(apiKey, hasApiKey, maskApiKey(apiKey), updatedAt, MOONSHOT_SERVICE_TARGET);
    }

    private String resolveUpdatedAt(Map<String, Object> settings,
                                    String updatedAtField,
                                    boolean hasApiKey,
                                    boolean fallbackLegacyUpdatedAtField) throws Exception {
        String updatedAt = readString(settings.get(updatedAtField));
        if (!updatedAt.isBlank()) {
            return updatedAt;
        }

        if (fallbackLegacyUpdatedAtField) {
            updatedAt = readString(settings.get(LEGACY_API_KEY_UPDATED_AT_FIELD));
            if (!updatedAt.isBlank()) {
                return updatedAt;
            }
        }

        if (hasApiKey && Files.exists(settingsPath)) {
            FileTime fileTime = Files.getLastModifiedTime(settingsPath);
            return fileTime.toInstant().toString();
        }
        return "";
    }

    private String resolveMoonshotUpdatedAt(Map<String, Object> settings,
                                            boolean hasApiKey) throws Exception {
        String updatedAt = readString(settings.get(MOONSHOT_API_KEY_UPDATED_AT_FIELD));
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
