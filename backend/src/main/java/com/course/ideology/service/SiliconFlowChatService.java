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
import java.util.List;
import java.util.Map;

@Service
public class SiliconFlowChatService {
    private static final Logger log = LoggerFactory.getLogger(SiliconFlowChatService.class);
    private static final int[] RETRY_BACKOFF_SECONDS = {16, 32, 64, 128, 256};

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final Duration timeout;

    public SiliconFlowChatService(ObjectMapper objectMapper,
                                  @Value("${app.siliconflow.baseUrl:https://api.siliconflow.cn/v1}") String baseUrl,
                                  @Value("${app.siliconflow.timeoutSeconds:90}") long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.timeout = Duration.ofSeconds(Math.max(30, timeoutSeconds));
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    public ChatResult chatCompletion(String apiKey, String model, String systemPrompt, String userPrompt) {
        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.2
        );

        try {
            String body = objectMapper.writeValueAsString(payload);
            return chatCompletionWithRetry(apiKey, body);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SiliconFlow request was interrupted.", ex);
        } catch (HttpTimeoutException ex) {
            throw new IllegalStateException("SiliconFlow request timed out.", ex);
        } catch (Exception ex) {
            if (ex instanceof IllegalStateException) {
                throw (IllegalStateException) ex;
            }
            throw new IllegalStateException("SiliconFlow request failed: " + ex.getMessage(), ex);
        }
    }

    private ChatResult chatCompletionWithRetry(String apiKey, String body) throws Exception {
        for (int retryCount = 0; ; retryCount++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
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
                return new ChatResult(content.trim(), raw);
            }

            boolean retriable = isRetriableStatus(statusCode);
            boolean canRetry = retriable && retryCount < RETRY_BACKOFF_SECONDS.length;
            if (canRetry) {
                int nextWait = RETRY_BACKOFF_SECONDS[retryCount];
                int currentRetry = retryCount + 1;
                log.warn(
                        "SiliconFlow transient failure: status={}, message={}, retry={}/{}, next_wait_seconds={}, trace_id={}",
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
                    "SiliconFlow request final failure: status={}, retriable={}, retries_used={}, trace_id={}, response={}",
                    statusCode,
                    retriable,
                    Math.min(retryCount, RETRY_BACKOFF_SECONDS.length),
                    traceId.isBlank() ? "-" : traceId,
                    abbreviate(raw)
            );
            throw new IllegalStateException(buildHttpFailureMessage(statusCode, raw, traceId));
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

    private String buildHttpFailureMessage(int statusCode, String raw, String traceId) {
        String message = "SiliconFlow request failed with HTTP " + statusCode + ". Response: " + abbreviate(raw);
        if (traceId != null && !traceId.isBlank()) {
            message += " TraceId: " + traceId;
        }
        return message;
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
