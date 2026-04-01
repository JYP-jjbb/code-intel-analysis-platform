package com.course.ideology.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class LlmProviderSettingsService {
    private final ObjectMapper objectMapper;
    private final Path settingsPath;

    public LlmProviderSettingsService(ObjectMapper objectMapper,
                                      @Value("${app.settings.file:workspace/settings/settings.json}") String configuredPath) {
        this.objectMapper = objectMapper;
        this.settingsPath = resolveSettingsPath(configuredPath);
    }

    public synchronized ProviderSettings getProviderSettings(String providerId) {
        ProviderMeta meta = ProviderMeta.of(providerId);
        try {
            Map<String, Object> settings = readSettings();
            return buildProviderSettings(meta, settings);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load provider settings for " + meta.providerId + ": " + ex.getMessage(), ex);
        }
    }

    public synchronized ProviderSettings saveProviderSettings(String providerId, ProviderSettingsUpdate update) {
        ProviderMeta meta = ProviderMeta.of(providerId);
        if (update == null) {
            throw new IllegalArgumentException("Provider settings payload cannot be null.");
        }
        try {
            Path parent = settingsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, Object> settings = readSettings();
            boolean hasAnyConfigChange = false;
            String now = Instant.now().toString();

            if (update.getApiKey() != null) {
                String trimmed = update.getApiKey().trim();
                settings.put(meta.apiKeyField, trimmed);
                settings.put(meta.apiKeyUpdatedAtField, now);
                hasAnyConfigChange = true;
            }
            if (update.getProviderName() != null) {
                settings.put(meta.providerNameField(), update.getProviderName().trim());
                hasAnyConfigChange = true;
            }
            if (update.getModelName() != null) {
                settings.put(meta.modelNameField(), update.getModelName().trim());
                hasAnyConfigChange = true;
            }
            if (update.getBaseUrl() != null) {
                settings.put(meta.baseUrlField(), update.getBaseUrl().trim());
                hasAnyConfigChange = true;
            }
            if (update.getEnableThinking() != null) {
                settings.put(meta.enableThinkingField(), update.getEnableThinking());
                hasAnyConfigChange = true;
            }
            if (update.getTemperature() != null) {
                settings.put(meta.temperatureField(), update.getTemperature());
                hasAnyConfigChange = true;
            }
            if (update.getMaxTokens() != null) {
                settings.put(meta.maxTokensField(), update.getMaxTokens());
                hasAnyConfigChange = true;
            }
            if (update.getStream() != null) {
                settings.put(meta.streamField(), update.getStream());
                hasAnyConfigChange = true;
            }
            if (update.getTimeout() != null) {
                settings.put(meta.timeoutField(), update.getTimeout());
                hasAnyConfigChange = true;
            }
            if (update.getExtraConfig() != null) {
                settings.put(meta.extraConfigField(), update.getExtraConfig().trim());
                hasAnyConfigChange = true;
            }
            if (hasAnyConfigChange) {
                settings.put(meta.configUpdatedAtField(), now);
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(settingsPath.toFile(), settings);
            return buildProviderSettings(meta, settings);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save provider settings for " + meta.providerId + ": " + ex.getMessage(), ex);
        }
    }

    private ProviderSettings buildProviderSettings(ProviderMeta meta, Map<String, Object> settings) {
        String apiKey = readString(settings.get(meta.apiKeyField));
        boolean hasApiKey = !apiKey.isBlank();
        String providerName = readString(settings.get(meta.providerNameField()));
        String modelName = readString(settings.get(meta.modelNameField()));
        String baseUrl = readString(settings.get(meta.baseUrlField()));
        String extraConfig = readString(settings.get(meta.extraConfigField()));
        String apiKeyUpdatedAt = readString(settings.get(meta.apiKeyUpdatedAtField));
        String configUpdatedAt = readString(settings.get(meta.configUpdatedAtField()));
        if (meta.allowLegacyApiKeyUpdatedAtFallback && apiKeyUpdatedAt.isBlank()) {
            apiKeyUpdatedAt = readString(settings.get("apiKeyUpdatedAt"));
        }

        if (providerName.isBlank()) {
            providerName = meta.defaultProviderName;
        }
        if (modelName.isBlank()) {
            modelName = meta.defaultModelName;
        }
        if (baseUrl.isBlank()) {
            baseUrl = meta.defaultBaseUrl;
        }
        if (extraConfig.isBlank()) {
            extraConfig = "";
        }

        Boolean enableThinking = readBoolean(settings.get(meta.enableThinkingField()), meta.defaultEnableThinking);
        Double temperature = readDouble(settings.get(meta.temperatureField()), meta.defaultTemperature);
        Integer maxTokens = readInt(settings.get(meta.maxTokensField()), meta.defaultMaxTokens);
        Boolean stream = readBoolean(settings.get(meta.streamField()), meta.defaultStream);
        Integer timeout = readInt(settings.get(meta.timeoutField()), meta.defaultTimeout);

        ProviderSettings output = new ProviderSettings();
        output.setProviderId(meta.providerId);
        output.setProviderName(providerName);
        output.setModelName(modelName);
        output.setBaseUrl(baseUrl);
        output.setEnableThinking(enableThinking);
        output.setTemperature(temperature);
        output.setMaxTokens(maxTokens);
        output.setStream(stream);
        output.setTimeout(timeout);
        output.setExtraConfig(extraConfig);
        output.setHasApiKey(hasApiKey);
        output.setMaskedApiKey(maskApiKey(apiKey));
        output.setApiKeyUpdatedAt(apiKeyUpdatedAt);
        output.setUpdatedAt(configUpdatedAt);
        output.setServiceTarget(meta.serviceTarget);
        return output;
    }

    private Map<String, Object> readSettings() throws Exception {
        if (!Files.exists(settingsPath) || Files.size(settingsPath) == 0L) {
            return new HashMap<>();
        }
        Map<String, Object> map = objectMapper.readValue(settingsPath.toFile(), new TypeReference<Map<String, Object>>() {
        });
        if (map == null) {
            return new HashMap<>();
        }
        return new HashMap<>(map);
    }

    private String readString(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private Boolean readBoolean(Object value, Boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
            return false;
        }
        return defaultValue;
    }

    private Double readDouble(Object value, Double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Integer readInt(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
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

    public static final class ProviderSettingsUpdate {
        private String providerName;
        private String modelName;
        private String apiKey;
        private String baseUrl;
        private Boolean enableThinking;
        private Double temperature;
        private Integer maxTokens;
        private Boolean stream;
        private Integer timeout;
        private String extraConfig;

        public String getProviderName() {
            return providerName;
        }

        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Boolean getEnableThinking() {
            return enableThinking;
        }

        public void setEnableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Boolean getStream() {
            return stream;
        }

        public void setStream(Boolean stream) {
            this.stream = stream;
        }

        public Integer getTimeout() {
            return timeout;
        }

        public void setTimeout(Integer timeout) {
            this.timeout = timeout;
        }

        public String getExtraConfig() {
            return extraConfig;
        }

        public void setExtraConfig(String extraConfig) {
            this.extraConfig = extraConfig;
        }
    }

    public static final class ProviderSettings {
        private String providerId;
        private String providerName;
        private String modelName;
        private String baseUrl;
        private Boolean enableThinking;
        private Double temperature;
        private Integer maxTokens;
        private Boolean stream;
        private Integer timeout;
        private String extraConfig;
        private boolean hasApiKey;
        private String maskedApiKey;
        private String apiKeyUpdatedAt;
        private String updatedAt;
        private String serviceTarget;

        public String getProviderId() {
            return providerId;
        }

        public void setProviderId(String providerId) {
            this.providerId = providerId;
        }

        public String getProviderName() {
            return providerName;
        }

        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }

        public String getModelName() {
            return modelName;
        }

        public void setModelName(String modelName) {
            this.modelName = modelName;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Boolean getEnableThinking() {
            return enableThinking;
        }

        public void setEnableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Boolean getStream() {
            return stream;
        }

        public void setStream(Boolean stream) {
            this.stream = stream;
        }

        public Integer getTimeout() {
            return timeout;
        }

        public void setTimeout(Integer timeout) {
            this.timeout = timeout;
        }

        public String getExtraConfig() {
            return extraConfig;
        }

        public void setExtraConfig(String extraConfig) {
            this.extraConfig = extraConfig;
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

        public String getApiKeyUpdatedAt() {
            return apiKeyUpdatedAt;
        }

        public void setApiKeyUpdatedAt(String apiKeyUpdatedAt) {
            this.apiKeyUpdatedAt = apiKeyUpdatedAt;
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

    private static final class ProviderMeta {
        private final String providerId;
        private final String defaultProviderName;
        private final String apiKeyField;
        private final String apiKeyUpdatedAtField;
        private final boolean allowLegacyApiKeyUpdatedAtFallback;
        private final String serviceTarget;
        private final String defaultModelName;
        private final String defaultBaseUrl;
        private final Boolean defaultEnableThinking;
        private final Double defaultTemperature;
        private final Integer defaultMaxTokens;
        private final Boolean defaultStream;
        private final Integer defaultTimeout;

        private ProviderMeta(String providerId,
                             String defaultProviderName,
                             String apiKeyField,
                             String apiKeyUpdatedAtField,
                             boolean allowLegacyApiKeyUpdatedAtFallback,
                             String serviceTarget,
                             String defaultModelName,
                             String defaultBaseUrl,
                             Boolean defaultEnableThinking,
                             Double defaultTemperature,
                             Integer defaultMaxTokens,
                             Boolean defaultStream,
                             Integer defaultTimeout) {
            this.providerId = providerId;
            this.defaultProviderName = defaultProviderName;
            this.apiKeyField = apiKeyField;
            this.apiKeyUpdatedAtField = apiKeyUpdatedAtField;
            this.allowLegacyApiKeyUpdatedAtFallback = allowLegacyApiKeyUpdatedAtFallback;
            this.serviceTarget = serviceTarget;
            this.defaultModelName = defaultModelName;
            this.defaultBaseUrl = defaultBaseUrl;
            this.defaultEnableThinking = defaultEnableThinking;
            this.defaultTemperature = defaultTemperature;
            this.defaultMaxTokens = defaultMaxTokens;
            this.defaultStream = defaultStream;
            this.defaultTimeout = defaultTimeout;
        }

        private static ProviderMeta of(String providerIdRaw) {
            String providerId = providerIdRaw == null ? "" : providerIdRaw.trim().toLowerCase(Locale.ROOT);
            switch (providerId) {
                case "deepseek":
                    return new ProviderMeta(
                            "deepseek",
                            "DeepSeek",
                            "deepseekApiKey",
                            "deepseekApiKeyUpdatedAt",
                            false,
                            "DeepSeek Official Chat Completions",
                            "deepseek-ai/DeepSeek-V3.2",
                            "https://api.deepseek.com",
                            false,
                            0.0,
                            null,
                            false,
                            90
                    );
                case "hunyuan":
                    return new ProviderMeta(
                            "hunyuan",
                            "Hunyuan",
                            "hunyuanApiKey",
                            "hunyuanApiKeyUpdatedAt",
                            false,
                            "Tencent Hunyuan Official Chat Completions",
                            "hunyuan-2.0-thinking-20251109",
                            "https://api.hunyuan.cloud.tencent.com/v1",
                            false,
                            0.0,
                            null,
                            false,
                            90
                    );
                case "qwen":
                    return new ProviderMeta(
                            "qwen",
                            "Qwen",
                            "qwenApiKey",
                            "qwenApiKeyUpdatedAt",
                            false,
                            "Qwen Official (DashScope Compatible) Chat Completions",
                            "qwen3.5-plus",
                            "https://dashscope.aliyuncs.com/compatible-mode/v1",
                            true,
                            0.0,
                            null,
                            false,
                            90
                    );
                case "moonshot":
                case "kimi":
                    return new ProviderMeta(
                            "kimi",
                            "Kimi",
                            "moonshotApiKey",
                            "moonshotApiKeyUpdatedAt",
                            true,
                            "Moonshot Official Chat Completions",
                            "kimi-k2.5",
                            "https://api.moonshot.cn/v1",
                            false,
                            1.0,
                            null,
                            false,
                            90
                    );
                default:
                    throw new IllegalArgumentException("Unsupported provider id: " + providerIdRaw);
            }
        }

        private String providerNameField() {
            return providerId + "ProviderName";
        }

        private String modelNameField() {
            return providerId + "ModelName";
        }

        private String baseUrlField() {
            return providerId + "BaseUrl";
        }

        private String enableThinkingField() {
            return providerId + "EnableThinking";
        }

        private String temperatureField() {
            return providerId + "Temperature";
        }

        private String maxTokensField() {
            return providerId + "MaxTokens";
        }

        private String streamField() {
            return providerId + "Stream";
        }

        private String timeoutField() {
            return providerId + "Timeout";
        }

        private String extraConfigField() {
            return providerId + "ExtraConfig";
        }

        private String configUpdatedAtField() {
            return providerId + "ConfigUpdatedAt";
        }
    }
}
