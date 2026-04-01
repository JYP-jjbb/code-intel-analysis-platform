package com.course.ideology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class MoonshotOfficialChatSmokeTest {

    @Test
    void shouldCallMoonshotOfficialChatCompletionWhenApiKeyConfigured() throws Exception {
        String moonshotApiKey = resolveEnvOrEmpty("MOONSHOT_API_KEY", "OPENAI_API_KEY");
        assumeTrue(!moonshotApiKey.isBlank(), "MOONSHOT_API_KEY (or OPENAI_API_KEY) is not configured, skipping real call.");

        Path tempSettings = Files.createTempFile("moonshot-smoke-settings", ".json");
        SiliconFlowSettingsService settingsService = new SiliconFlowSettingsService(new ObjectMapper(), tempSettings.toString());
        SiliconFlowChatService service = new SiliconFlowChatService(
                new ObjectMapper(),
                settingsService,
                resolveEnvOrDefault("https://api.moonshot.cn/v1", "MOONSHOT_BASE_URL", "OPENAI_BASE_URL"),
                moonshotApiKey,
                resolveEnvOrDefault("kimi-k2.5", "MOONSHOT_MODEL"),
                resolveEnvOrDefault("https://api.hunyuan.cloud.tencent.com/v1", "HUNYUAN_BASE_URL"),
                resolveEnvOrDefault("", "HUNYUAN_API_KEY"),
                resolveEnvOrDefault("hunyuan-2.0-thinking-20251109", "HUNYUAN_MODEL"),
                resolveEnvOrDefault("https://dashscope.aliyuncs.com/compatible-mode/v1", "QWEN_BASE_URL"),
                resolveEnvOrDefault("", "QWEN_API_KEY"),
                resolveEnvOrDefault("qwen3.5-plus", "QWEN_MODEL"),
                90,
                "https://api.deepseek.com",
                ""
        );

        Map<String, Object> dispatch = service.buildDebugDispatchPreview("kimi-k2.5");
        String endpoint = String.valueOf(dispatch.getOrDefault("base_url", "https://api.moonshot.cn/v1")) + "/chat/completions";
        String model = String.valueOf(dispatch.getOrDefault("upstream_model", "kimi-k2.5"));

        SiliconFlowChatService.ChatResult result = service.chatCompletion(
                "",
                "kimi-k2.5",
                "You are a concise assistant.",
                "Reply with one short token only: pong."
        );

        String content = result.getContent() == null ? "" : result.getContent().trim();
        String summary = content.length() <= 120 ? content : content.substring(0, 120) + "...";

        System.out.println("MOONSHOT_SMOKE endpoint=" + endpoint + ", model=" + model + ", summary=" + summary);
        assertFalse(content.isBlank(), "Moonshot response should not be empty.");
    }

    private String resolveEnvOrEmpty(String... keys) {
        if (keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            String value = System.getenv(key);
            if (value != null && !value.trim().isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String resolveEnvOrDefault(String defaultValue, String... keys) {
        String value = resolveEnvOrEmpty(keys);
        if (!value.isBlank()) {
            return value;
        }
        return defaultValue == null ? "" : defaultValue;
    }
}
