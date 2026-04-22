package com.course.ideology.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SiliconFlowChatService {
    private static final Logger log = LoggerFactory.getLogger(SiliconFlowChatService.class);
    private static final int[] RETRY_BACKOFF_SECONDS = {16, 32, 64, 128, 256};
    private static final String DEFAULT_MOONSHOT_MODEL = "kimi-k2.5";
    private static final String DEFAULT_HUNYUAN_MODEL = "hunyuan-2.0-thinking-20251109";
    private static final String DEFAULT_QWEN_MODEL = "qwen3.5-plus";
    private static final String DEFAULT_DEEPSEEK_CHAT_MODEL = "deepseek-chat";
    private static final String DEFAULT_DEEPSEEK_REASONER_MODEL = "deepseek-reasoner";
    private static final double DEEPSEEK_TEMPERATURE = 0.0;
    private static final double KIMI_TEMPERATURE = 1.0;
    private static final double HUNYUAN_TEMPERATURE = 0.0;
    private static final double QWEN_TEMPERATURE = 0.0;

    private final ObjectMapper objectMapper;
    private final SiliconFlowSettingsService settingsService;
    private final HttpClient httpClient;
    private final String moonshotBaseUrl;
    private final String moonshotApiKeyFromConfig;
    private final String moonshotModel;
    private final String hunyuanBaseUrl;
    private final String hunyuanApiKeyFromConfig;
    private final String hunyuanModel;
    private final String qwenBaseUrl;
    private final String qwenApiKeyFromConfig;
    private final String qwenModel;
    private final String deepSeekBaseUrl;
    private final String deepSeekApiKeyFromConfig;
    private final String deepSeekChatModel;
    private final String deepSeekReasonerModel;
    private final Duration timeout;

    public SiliconFlowChatService(ObjectMapper objectMapper,
                                  SiliconFlowSettingsService settingsService,
                                  @Value("${app.moonshot.base-url:${MOONSHOT_BASE_URL:${OPENAI_BASE_URL:https://api.moonshot.cn/v1}}}") String moonshotBaseUrl,
                                  @Value("${app.moonshot.api-key:${MOONSHOT_API_KEY:${OPENAI_API_KEY:}}}") String moonshotApiKey,
                                  @Value("${app.moonshot.model:${MOONSHOT_MODEL:kimi-k2.5}}") String moonshotModel,
                                  @Value("${app.hunyuan.base-url:${HUNYUAN_BASE_URL:https://api.hunyuan.cloud.tencent.com/v1}}") String hunyuanBaseUrl,
                                  @Value("${app.hunyuan.api-key:${HUNYUAN_API_KEY:${OPENAI_API_KEY:}}}") String hunyuanApiKey,
                                  @Value("${app.hunyuan.model:${HUNYUAN_MODEL:hunyuan-2.0-thinking-20251109}}") String hunyuanModel,
                                  @Value("${app.qwen.base-url:${QWEN_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}}") String qwenBaseUrl,
                                  @Value("${app.qwen.api-key:${QWEN_API_KEY:}}") String qwenApiKey,
                                  @Value("${app.qwen.model:${QWEN_MODEL:qwen3.5-plus}}") String qwenModel,
                                  @Value("${app.moonshot.timeoutSeconds:90}") long timeoutSeconds,
                                  @Value("${app.deepseek.base-url:${DEEPSEEK_BASE_URL:https://api.deepseek.com}}") String deepSeekBaseUrl,
                                  @Value("${app.deepseek.chat-model:${DEEPSEEK_CHAT_MODEL:deepseek-chat}}") String deepSeekChatModel,
                                  @Value("${app.deepseek.reasoner-model:${DEEPSEEK_REASONER_MODEL:${app.deepseek.model:${DEEPSEEK_MODEL:deepseek-reasoner}}}}") String deepSeekReasonerModel,
                                  @Value("${app.deepseek.api-key:${DEEPSEEK_API_KEY:}}") String deepSeekApiKey) {
        this.objectMapper = objectMapper;
        this.settingsService = settingsService;
        this.moonshotBaseUrl = normalizeBaseUrl(moonshotBaseUrl);
        this.moonshotApiKeyFromConfig = moonshotApiKey == null ? "" : moonshotApiKey.trim();
        this.moonshotModel = normalizeMoonshotModel(moonshotModel);
        this.hunyuanBaseUrl = normalizeBaseUrl(hunyuanBaseUrl);
        this.hunyuanApiKeyFromConfig = hunyuanApiKey == null ? "" : hunyuanApiKey.trim();
        this.hunyuanModel = normalizeHunyuanModel(hunyuanModel);
        this.qwenBaseUrl = normalizeBaseUrl(qwenBaseUrl);
        this.qwenApiKeyFromConfig = qwenApiKey == null ? "" : qwenApiKey.trim();
        this.qwenModel = normalizeQwenModel(qwenModel);
        this.deepSeekBaseUrl = normalizeBaseUrl(deepSeekBaseUrl);
        this.deepSeekChatModel = normalizeDeepSeekChatModel(deepSeekChatModel);
        this.deepSeekReasonerModel = normalizeDeepSeekReasonerModel(deepSeekReasonerModel);
        this.deepSeekApiKeyFromConfig = deepSeekApiKey == null ? "" : deepSeekApiKey.trim();
        this.timeout = Duration.ofSeconds(Math.max(30, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public boolean hasApiCredentialForModel(String model, String preferredApiKey) {
        RoutingDecision routing = routeModel(model, "credential-check");
        if (routing.baseUrl == null || routing.baseUrl.isBlank()) {
            log.warn("LLM credential check failed: provider={}, requested_model={}, reason=base_url_missing",
                    routing.providerName, routing.requestedModel);
            return false;
        }
        CredentialResolution credential = resolveCredential(routing, preferredApiKey);
        log.info("LLM credential check: provider={}, base_url={}, requested_model={}, key_source={}, key_present={}",
                routing.providerName,
                routing.baseUrl,
                routing.requestedModel,
                credential.source,
                !credential.apiKey.isBlank());
        return !credential.apiKey.isBlank();
    }

    public String buildMissingCredentialMessage(String model) {
        RoutingDecision routing = routeModel(model, "credential-check");
        if (routing.baseUrl == null || routing.baseUrl.isBlank()) {
            if (routing.provider == Provider.DEEPSEEK_OFFICIAL) {
                return "DeepSeek base URL is not configured. Set DEEPSEEK_BASE_URL (or app.deepseek.base-url).";
            }
            if (routing.provider == Provider.HUNYUAN_OFFICIAL) {
                return "Tencent Hunyuan base URL is not configured. Set HUNYUAN_BASE_URL (or app.hunyuan.base-url).";
            }
            if (routing.provider == Provider.QWEN_OFFICIAL) {
                return "Qwen base URL is not configured. Set QWEN_BASE_URL (or app.qwen.base-url).";
            }
            return "Moonshot base URL is not configured. Set MOONSHOT_BASE_URL (or app.moonshot.base-url).";
        }
        CredentialResolution credential = resolveCredential(routing, "");
        if (routing.provider == Provider.DEEPSEEK_OFFICIAL) {
            return "DeepSeek API key is not configured. Missing at: " + credential.source + ".";
        }
        if (routing.provider == Provider.HUNYUAN_OFFICIAL) {
            return "Tencent Hunyuan API key is not configured. Missing at: " + credential.source + ".";
        }
        if (routing.provider == Provider.QWEN_OFFICIAL) {
            return "Qwen Official API key is not configured. Missing at: " + credential.source + ".";
        }
        return "Moonshot Official API key is not configured. Missing at: " + credential.source + ".";
    }

    Map<String, Object> buildDebugDispatchPreview(String model) {
        RoutingDecision routing = routeModel(model, "unknown");
        return routing.toDebugMap();
    }

    Map<String, Object> buildDebugDispatchPreview(String model, String requestModule) {
        RoutingDecision routing = routeModel(model, requestModule);
        return routing.toDebugMap();
    }

    public ChatResult chatCompletion(String apiKey, String model, String systemPrompt, String userPrompt) {
        return chatCompletion(apiKey, model, systemPrompt, userPrompt, "unknown");
    }

    public ChatResult chatCompletion(String apiKey,
                                     String model,
                                     String systemPrompt,
                                     String userPrompt,
                                     String requestModule) {
        String module = requestModule == null || requestModule.isBlank() ? "unknown" : requestModule.trim();
        RoutingDecision routing = routeModel(model, module);
        int attemptIndex = extractAttemptIndex(module);
        if (routing.provider == Provider.DEEPSEEK_OFFICIAL && routing.deepSeekModeForced) {
            log.info(
                    "DeepSeek scenario routing: module={}, requested_model={}, forced_mode={}, upstream_model={}",
                    module,
                    routing.requestedModel,
                    routing.modeType,
                    routing.upstreamModel
            );
        }
        CredentialResolution credential = resolveCredential(routing, apiKey);
        String resolvedApiKey = requireCredential(routing, credential);
        Map<String, Object> payload = buildChatCompletionPayload(routing, systemPrompt, userPrompt);

        try {
            Map<String, Object> requestParams = extractRequestControlParams(payload, routing, module);
            log.info(
                    "LLM request dispatch: provider={}, module={}, mode_type={}, attempt_index={}, base_url={}, requested_model={}, upstream_model={}, reasoning={}, key_source={}, request_params={}",
                    routing.providerName,
                    module,
                    routing.modeType,
                    attemptIndex,
                    routing.baseUrl,
                    routing.requestedModel,
                    routing.upstreamModel,
                    routing.reasoning,
                    credential.source,
                    objectMapper.writeValueAsString(requestParams)
            );
            String body = objectMapper.writeValueAsString(payload);
            return chatCompletionWithRetry(routing, resolvedApiKey, body, module);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(routing.providerName + " request was interrupted.", ex);
        } catch (HttpTimeoutException ex) {
            throw new IllegalStateException(routing.providerName + " request timed out.", ex);
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException) {
                throw (IllegalStateException) ex;
            }
            throw new IllegalStateException(routing.providerName + " request failed: " + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> buildChatCompletionPayload(RoutingDecision routing, String systemPrompt, String userPrompt) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", routing.upstreamModel);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        payload.put("temperature", routing.temperature);
        if (routing.enableThinking) {
            payload.put("extra_body", Map.of("enable_thinking", true));
        }
        return payload;
    }

    private Map<String, Object> extractRequestControlParams(Map<String, Object> payload,
                                                            RoutingDecision routing,
                                                            String requestModule) {
        LinkedHashMap<String, Object> control = new LinkedHashMap<>();
        control.put("model", payload.get("model"));
        control.put("temperature", payload.get("temperature"));
        control.put("extra_body", payload.get("extra_body"));
        control.put("mode_type", routing.modeType);
        control.put("reasoning", routing.reasoning);
        control.put("deepseek_mode_forced", routing.deepSeekModeForced);
        control.put("request_module", requestModule);
        control.put("attempt_index", extractAttemptIndex(requestModule));
        return control;
    }

    private RoutingDecision routeModel(String requestedModel, String requestModule) {
        String rawModel = requestedModel == null ? "" : requestedModel.trim();
        if (rawModel.isBlank()) {
            rawModel = moonshotModel;
        }
        String module = requestModule == null ? "" : requestModule.trim();
        String normalized = normalizeModel(rawModel);
        if (isDeepSeekModel(normalized)) {
            DeepSeekModeDecision deepSeekModeDecision = resolveDeepSeekMode(rawModel, module);
            return new RoutingDecision(
                    Provider.DEEPSEEK_OFFICIAL,
                    "deepseek_official",
                    deepSeekBaseUrl,
                    rawModel,
                    deepSeekModeDecision.upstreamModel,
                    DEEPSEEK_TEMPERATURE,
                    false,
                    deepSeekModeDecision.reasoning,
                    deepSeekModeDecision.modeType,
                    deepSeekModeDecision.forcedByScenario
            );
        }
        if (isHunyuanModel(normalized)) {
            return new RoutingDecision(
                    Provider.HUNYUAN_OFFICIAL,
                    "hunyuan_official",
                    hunyuanBaseUrl,
                    rawModel,
                    resolveHunyuanUpstreamModel(rawModel),
                    HUNYUAN_TEMPERATURE,
                    false,
                    false,
                    "chat",
                    false
            );
        }
        if (isQwenModel(normalized)) {
            return new RoutingDecision(
                    Provider.QWEN_OFFICIAL,
                    "qwen_official",
                    qwenBaseUrl,
                    rawModel,
                    resolveQwenUpstreamModel(rawModel),
                    QWEN_TEMPERATURE,
                    true,
                    true,
                    "reasoner",
                    false
            );
        }
        return new RoutingDecision(
                Provider.MOONSHOT_OFFICIAL,
                "moonshot_official",
                moonshotBaseUrl,
                rawModel,
                resolveMoonshotUpstreamModel(rawModel),
                KIMI_TEMPERATURE,
                false,
                false,
                "chat",
                false
        );
    }

    private String normalizeModel(String model) {
        if (model == null) {
            return "";
        }
        return model.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isDeepSeekModel(String normalizedModel) {
        return normalizedModel.contains("deepseek");
    }

    private boolean isHunyuanModel(String normalizedModel) {
        return normalizedModel.contains("hunyuan");
    }

    private boolean isQwenModel(String normalizedModel) {
        return normalizedModel.contains("qwen");
    }

    private String normalizeMoonshotModel(String configuredModel) {
        String model = configuredModel == null ? "" : configuredModel.trim();
        if (model.isBlank()) {
            return DEFAULT_MOONSHOT_MODEL;
        }
        return model;
    }

    private String normalizeHunyuanModel(String configuredModel) {
        String model = configuredModel == null ? "" : configuredModel.trim();
        if (model.isBlank()) {
            return DEFAULT_HUNYUAN_MODEL;
        }
        return model;
    }

    private String normalizeQwenModel(String configuredModel) {
        String model = configuredModel == null ? "" : configuredModel.trim();
        if (model.isBlank()) {
            return DEFAULT_QWEN_MODEL;
        }
        return model;
    }

    private String normalizeDeepSeekChatModel(String configuredModel) {
        String model = configuredModel == null ? "" : configuredModel.trim();
        if (model.isBlank()) {
            return DEFAULT_DEEPSEEK_CHAT_MODEL;
        }
        String normalized = model.toLowerCase(Locale.ROOT);
        if (normalized.contains("chat")) {
            return DEFAULT_DEEPSEEK_CHAT_MODEL;
        }
        if (normalized.contains("deepseek")) {
            log.warn("DeepSeek chat model '{}' is invalid, forcing '{}'.", model, DEFAULT_DEEPSEEK_CHAT_MODEL);
            return DEFAULT_DEEPSEEK_CHAT_MODEL;
        }
        log.warn("Unsupported DeepSeek chat model token '{}', forcing '{}'.", model, DEFAULT_DEEPSEEK_CHAT_MODEL);
        return DEFAULT_DEEPSEEK_CHAT_MODEL;
    }

    private String normalizeDeepSeekReasonerModel(String configuredModel) {
        String model = configuredModel == null ? "" : configuredModel.trim();
        if (model.isBlank()) {
            return DEFAULT_DEEPSEEK_REASONER_MODEL;
        }
        String normalized = model.toLowerCase(Locale.ROOT);
        if (normalized.contains("reasoner")) {
            return DEFAULT_DEEPSEEK_REASONER_MODEL;
        }
        if (normalized.contains("deepseek")) {
            log.warn("DeepSeek reasoner model '{}' is invalid, forcing '{}'.", model, DEFAULT_DEEPSEEK_REASONER_MODEL);
            return DEFAULT_DEEPSEEK_REASONER_MODEL;
        }
        log.warn("Unsupported DeepSeek reasoner model token '{}', forcing '{}'.", model, DEFAULT_DEEPSEEK_REASONER_MODEL);
        return DEFAULT_DEEPSEEK_REASONER_MODEL;
    }

    private DeepSeekModeDecision resolveDeepSeekMode(String requestedModel, String requestModule) {
        String module = requestModule == null ? "" : requestModule.trim().toLowerCase(Locale.ROOT);
        String requested = normalizeModelToken(requestedModel).toLowerCase(Locale.ROOT);
        DeepSeekMode requestedMode = parseRequestedDeepSeekMode(requested);
        boolean batchReasoner = isBatchVerificationModule(module);
        DeepSeekMode targetMode = batchReasoner ? DeepSeekMode.REASONER : DeepSeekMode.CHAT;
        boolean forcedByScenario = requestedMode != DeepSeekMode.UNSPECIFIED && requestedMode != targetMode;
        String modeType = targetMode == DeepSeekMode.REASONER ? "reasoner" : "chat";
        return new DeepSeekModeDecision(
                targetMode == DeepSeekMode.REASONER ? deepSeekReasonerModel : deepSeekChatModel,
                targetMode == DeepSeekMode.REASONER,
                modeType,
                forcedByScenario
        );
    }

    private boolean isBatchVerificationModule(String module) {
        if (module == null || module.isBlank()) {
            return false;
        }
        return module.contains("nutera.batch.");
    }

    private DeepSeekMode parseRequestedDeepSeekMode(String normalizedRequestedModel) {
        if (normalizedRequestedModel == null || normalizedRequestedModel.isBlank()) {
            return DeepSeekMode.UNSPECIFIED;
        }
        if (normalizedRequestedModel.contains("reasoner")) {
            return DeepSeekMode.REASONER;
        }
        if (normalizedRequestedModel.contains("chat")) {
            return DeepSeekMode.CHAT;
        }
        return DeepSeekMode.UNSPECIFIED;
    }

    private int extractAttemptIndex(String requestModule) {
        if (requestModule == null || requestModule.isBlank()) {
            return 0;
        }
        String normalized = requestModule.trim().toLowerCase(Locale.ROOT);
        int marker = normalized.lastIndexOf(".attempt-");
        if (marker < 0) {
            return 0;
        }
        String suffix = normalized.substring(marker + ".attempt-".length()).trim();
        try {
            return Math.max(0, Integer.parseInt(suffix));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String resolveMoonshotUpstreamModel(String requestedModel) {
        String cleaned = normalizeModelToken(requestedModel);
        if (cleaned.isBlank()) {
            return moonshotModel;
        }
        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (normalized.contains("kimi-k2.5")
                || normalized.contains("kimi-k2_5")
                || normalized.contains("kimi-k2")
                || normalized.contains("moonshotai/kimi-k2.5")) {
            return "kimi-k2.5";
        }
        return cleaned;
    }

    private String resolveHunyuanUpstreamModel(String requestedModel) {
        String cleaned = normalizeModelToken(requestedModel);
        if (cleaned.isBlank()) {
            return hunyuanModel;
        }
        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (normalized.contains("hunyuan")) {
            return hunyuanModel;
        }
        return cleaned;
    }

    private String resolveQwenUpstreamModel(String requestedModel) {
        String cleaned = normalizeModelToken(requestedModel);
        if (cleaned.isBlank()) {
            return qwenModel;
        }
        String normalized = cleaned.toLowerCase(Locale.ROOT);
        if (normalized.contains("qwen")) {
            return qwenModel;
        }
        return cleaned;
    }

    private String normalizeModelToken(String rawModel) {
        String token = rawModel == null ? "" : rawModel.trim();
        if (token.isBlank()) {
            return "";
        }
        token = token.replaceAll("^[\"'`\\s]+|[\"'`\\s]+$", "");
        int markerIndex = token.indexOf("--model");
        if (markerIndex >= 0) {
            String suffix = token.substring(markerIndex + "--model".length()).trim();
            if (!suffix.isBlank()) {
                token = suffix.split("\\s+")[0];
            }
        }
        if (token.contains("/")) {
            String[] segments = token.split("/");
            token = segments[segments.length - 1];
        }
        return token.replaceAll("^[\"'`]+|[\"'`]+$", "").trim();
    }

    private CredentialResolution resolveCredential(RoutingDecision routing, String preferredApiKey) {
        if (routing.provider == Provider.DEEPSEEK_OFFICIAL) {
            SiliconFlowSettingsService.ResolvedApiKey settingsCredential = settingsService.resolveDeepSeekApiKey();
            String settingsKey = settingsCredential.getApiKey() == null ? "" : settingsCredential.getApiKey().trim();
            if (!settingsKey.isBlank()) {
                return new CredentialResolution(settingsKey, settingsCredential.getSource());
            }
            if (!deepSeekApiKeyFromConfig.isBlank()) {
                return new CredentialResolution(deepSeekApiKeyFromConfig, "app.deepseek.api-key/DEEPSEEK_API_KEY");
            }
            return new CredentialResolution("", "app.deepseek.api-key/DEEPSEEK_API_KEY");
        }
        if (routing.provider == Provider.HUNYUAN_OFFICIAL) {
            SiliconFlowSettingsService.ResolvedApiKey settingsCredential = settingsService.resolveHunyuanApiKey();
            String settingsKey = settingsCredential.getApiKey() == null ? "" : settingsCredential.getApiKey().trim();
            if (!settingsKey.isBlank()) {
                return new CredentialResolution(settingsKey, settingsCredential.getSource());
            }
            if (!hunyuanApiKeyFromConfig.isBlank()) {
                return new CredentialResolution(hunyuanApiKeyFromConfig, "app.hunyuan.api-key/HUNYUAN_API_KEY/OPENAI_API_KEY");
            }
            return new CredentialResolution("", "app.hunyuan.api-key/HUNYUAN_API_KEY/OPENAI_API_KEY");
        }
        if (routing.provider == Provider.QWEN_OFFICIAL) {
            SiliconFlowSettingsService.ResolvedApiKey settingsCredential = settingsService.resolveQwenApiKey();
            String settingsKey = settingsCredential.getApiKey() == null ? "" : settingsCredential.getApiKey().trim();
            if (!settingsKey.isBlank()) {
                return new CredentialResolution(settingsKey, settingsCredential.getSource());
            }
            if (!qwenApiKeyFromConfig.isBlank()) {
                return new CredentialResolution(qwenApiKeyFromConfig, "app.qwen.api-key/QWEN_API_KEY");
            }
            return new CredentialResolution("", "app.qwen.api-key/QWEN_API_KEY");
        }
        String key = preferredApiKey == null ? "" : preferredApiKey.trim();
        if (!key.isBlank()) {
            return new CredentialResolution(key, "settings.moonshotApiKey(request)");
        }
        SiliconFlowSettingsService.ResolvedApiKey settingsCredential = settingsService.resolveMoonshotApiKey();
        String settingsKey = settingsCredential.getApiKey() == null ? "" : settingsCredential.getApiKey().trim();
        if (!settingsKey.isBlank()) {
            return new CredentialResolution(settingsKey, settingsCredential.getSource());
        }
        if (!moonshotApiKeyFromConfig.isBlank()) {
            return new CredentialResolution(moonshotApiKeyFromConfig, "app.moonshot.api-key/MOONSHOT_API_KEY/OPENAI_API_KEY");
        }
        return new CredentialResolution("", "app.moonshot.api-key/MOONSHOT_API_KEY/OPENAI_API_KEY");
    }

    private String requireCredential(RoutingDecision routing, CredentialResolution credential) {
        if (routing.baseUrl == null || routing.baseUrl.isBlank()) {
            if (routing.provider == Provider.DEEPSEEK_OFFICIAL) {
                throw new IllegalStateException("DeepSeek base URL is missing. Set DEEPSEEK_BASE_URL (or app.deepseek.base-url).");
            }
            if (routing.provider == Provider.HUNYUAN_OFFICIAL) {
                throw new IllegalStateException("Tencent Hunyuan base URL is missing. Set HUNYUAN_BASE_URL (or app.hunyuan.base-url).");
            }
            if (routing.provider == Provider.QWEN_OFFICIAL) {
                throw new IllegalStateException("Qwen base URL is missing. Set QWEN_BASE_URL (or app.qwen.base-url).");
            }
            throw new IllegalStateException("Moonshot base URL is missing. Set MOONSHOT_BASE_URL (or app.moonshot.base-url).");
        }
        if (credential.apiKey.isBlank()) {
            if (routing.provider == Provider.DEEPSEEK_OFFICIAL) {
                throw new IllegalStateException("DeepSeek API key is missing at source: " + credential.source + ".");
            }
            if (routing.provider == Provider.HUNYUAN_OFFICIAL) {
                throw new IllegalStateException("Tencent Hunyuan API key is missing at source: " + credential.source + ".");
            }
            if (routing.provider == Provider.QWEN_OFFICIAL) {
                throw new IllegalStateException("Qwen Official API key is missing at source: " + credential.source + ".");
            }
            throw new IllegalStateException("Moonshot Official API key is missing at source: " + credential.source + ".");
        }
        return credential.apiKey;
    }

    private ChatResult chatCompletionWithRetry(RoutingDecision routing, String apiKey, String body, String requestModule) throws Exception {
        for (int retryCount = 0; ; retryCount++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(routing.baseUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            String raw = response.body() == null ? "" : response.body();
            String traceId = extractTraceId(response);

            if (statusCode >= 200 && statusCode < 300) {
                JsonNode root = objectMapper.readTree(raw);
                String content = root.path("choices").path(0).path("message").path("content").asText("");
                if (content == null || content.isBlank()) {
                    throw new IllegalStateException("Model response is empty. No candidate function was generated.");
                }
                log.info(
                        "{} response success: module={}, mode_type={}, endpoint={}, upstream_model={}, reasoning={}, content_preview={}",
                        routing.providerName,
                        requestModule,
                        routing.modeType,
                        routing.baseUrl + "/chat/completions",
                        routing.upstreamModel,
                        routing.reasoning,
                        abbreviate(content)
                );
                return new ChatResult(content.trim(), raw);
            }

            boolean retriable = isRetriableStatus(statusCode);
            boolean canRetry = retriable && retryCount < RETRY_BACKOFF_SECONDS.length;
            if (canRetry) {
                int nextWait = RETRY_BACKOFF_SECONDS[retryCount];
                int currentRetry = retryCount + 1;
                log.warn(
                        "{} transient failure: status={}, message={}, retry={}/{}, next_wait_seconds={}, trace_id={}",
                        routing.providerName,
                        statusCode,
                        abbreviate(raw),
                        currentRetry,
                        RETRY_BACKOFF_SECONDS.length,
                        nextWait,
                        traceId.isBlank() ? "-" : traceId
                );
                sleepSeconds(nextWait);
                continue;
            }

            log.error(
                    "{} request final failure: status={}, retriable={}, retries_used={}, trace_id={}, response={}",
                    routing.providerName,
                    statusCode,
                    retriable,
                    Math.min(retryCount, RETRY_BACKOFF_SECONDS.length),
                    traceId.isBlank() ? "-" : traceId,
                    abbreviate(raw)
            );
            throw new IllegalStateException(buildHttpFailureMessage(routing.providerName, statusCode, raw, traceId));
        }
    }

    private boolean isRetriableStatus(int statusCode) {
        return statusCode == 429 || statusCode == 503 || statusCode == 504;
    }

    private void sleepSeconds(int seconds) throws InterruptedException {
        Thread.sleep(Duration.ofSeconds(seconds).toMillis());
    }

    private String extractTraceId(HttpResponse<?> response) {
        return response.headers().firstValue("x-trace-id")
                .or(() -> response.headers().firstValue("trace-id"))
                .or(() -> response.headers().firstValue("x-request-id"))
                .or(() -> response.headers().firstValue("request-id"))
                .or(() -> response.headers().firstValue("traceparent"))
                .map(String::trim)
                .orElse("");
    }

    private String buildHttpFailureMessage(String providerName, int statusCode, String raw, String traceId) {
        String message = providerName + " request failed with HTTP " + statusCode + ". Response: " + abbreviate(raw);
        if (traceId != null && !traceId.isBlank()) {
            message += " TraceId: " + traceId;
        }
        return message;
    }

    private String normalizeBaseUrl(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isBlank()) {
            return "";
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private enum Provider {
        MOONSHOT_OFFICIAL,
        HUNYUAN_OFFICIAL,
        DEEPSEEK_OFFICIAL,
        QWEN_OFFICIAL
    }

    private static final class CredentialResolution {
        private final String apiKey;
        private final String source;

        private CredentialResolution(String apiKey, String source) {
            this.apiKey = apiKey == null ? "" : apiKey.trim();
            this.source = source == null || source.isBlank() ? "unknown" : source;
        }
    }

    private static final class RoutingDecision {
        private final Provider provider;
        private final String providerName;
        private final String baseUrl;
        private final String requestedModel;
        private final String upstreamModel;
        private final double temperature;
        private final boolean enableThinking;
        private final boolean reasoning;
        private final String modeType;
        private final boolean deepSeekModeForced;

        private RoutingDecision(Provider provider,
                                String providerName,
                                String baseUrl,
                                String requestedModel,
                                String upstreamModel,
                                double temperature,
                                boolean enableThinking,
                                boolean reasoning,
                                String modeType,
                                boolean deepSeekModeForced) {
            this.provider = provider;
            this.providerName = providerName;
            this.baseUrl = baseUrl;
            this.requestedModel = requestedModel;
            this.upstreamModel = upstreamModel;
            this.temperature = temperature;
            this.enableThinking = enableThinking;
            this.reasoning = reasoning;
            this.modeType = modeType == null || modeType.isBlank() ? "chat" : modeType;
            this.deepSeekModeForced = deepSeekModeForced;
        }

        private Map<String, Object> toDebugMap() {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("provider", providerName);
            map.put("base_url", baseUrl);
            map.put("requested_model", requestedModel);
            map.put("upstream_model", upstreamModel);
            map.put("temperature", temperature);
            map.put("enable_thinking", enableThinking);
            map.put("mode_type", modeType);
            map.put("reasoning", reasoning);
            map.put("deepseek_mode_forced", deepSeekModeForced);
            return map;
        }
    }

    private enum DeepSeekMode {
        CHAT,
        REASONER,
        UNSPECIFIED
    }

    private static final class DeepSeekModeDecision {
        private final String upstreamModel;
        private final boolean reasoning;
        private final String modeType;
        private final boolean forcedByScenario;

        private DeepSeekModeDecision(String upstreamModel, boolean reasoning, String modeType, boolean forcedByScenario) {
            this.upstreamModel = upstreamModel;
            this.reasoning = reasoning;
            this.modeType = modeType;
            this.forcedByScenario = forcedByScenario;
        }
    }

    private String abbreviate(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.replace("\n", " ").replace("\r", " ");
        if (normalized.length() <= 300) {
            return normalized;
        }
        return normalized.substring(0, 300) + "...";
    }

    public static class ChatResult {
        private final String content;
        private final String rawResponse;

        public ChatResult(String content, String rawResponse) {
            this.content = content;
            this.rawResponse = rawResponse;
        }

        public String getContent() {
            return content;
        }

        public String getRawResponse() {
            return rawResponse;
        }
    }
}
