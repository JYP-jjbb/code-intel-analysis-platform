package com.course.ideology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SiliconFlowChatServiceCredentialResolutionTest {

    @Test
    void shouldResolveDeepSeekCredentialFromPersistedSettingsBeforeEnv() throws Exception {
        Path tempSettings = Files.createTempFile("llm-settings", ".json");
        SiliconFlowSettingsService settingsService = new SiliconFlowSettingsService(new ObjectMapper(), tempSettings.toString());
        settingsService.saveDeepSeekApiKey("sk-deepseek-from-settings");

        SiliconFlowChatService service = new SiliconFlowChatService(
                new ObjectMapper(),
                settingsService,
                "https://api.moonshot.cn/v1",
                "sk-moonshot-from-env",
                "kimi-k2.5",
                "https://api.hunyuan.cloud.tencent.com/v1",
                "sk-hunyuan-from-env",
                "hunyuan-2.0-thinking-20251109",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "sk-qwen-from-env",
                "qwen3.5-plus",
                90,
                "https://api.deepseek.com",
                ""
        );

        boolean hasCredential = service.hasApiCredentialForModel("deepseek-ai/DeepSeek-V3.2", settingsService.getApiKey());
        assertTrue(hasCredential);
    }

    @Test
    void shouldResolveMoonshotCredentialFromPersistedSettings() throws Exception {
        Path tempSettings = Files.createTempFile("llm-settings", ".json");
        SiliconFlowSettingsService settingsService = new SiliconFlowSettingsService(new ObjectMapper(), tempSettings.toString());
        settingsService.saveMoonshotApiKey("sk-moonshot-from-settings");

        SiliconFlowChatService service = new SiliconFlowChatService(
                new ObjectMapper(),
                settingsService,
                "https://api.moonshot.cn/v1",
                "",
                "kimi-k2.5",
                "https://api.hunyuan.cloud.tencent.com/v1",
                "",
                "hunyuan-2.0-thinking-20251109",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "",
                "qwen3.5-plus",
                90,
                "https://api.deepseek.com",
                ""
        );

        boolean hasCredential = service.hasApiCredentialForModel("kimi-k2.5", settingsService.getApiKey());
        assertTrue(hasCredential);
    }

    @Test
    void shouldResolveHunyuanCredentialFromConfiguredEnv() throws Exception {
        Path tempSettings = Files.createTempFile("llm-settings", ".json");
        SiliconFlowSettingsService settingsService = new SiliconFlowSettingsService(new ObjectMapper(), tempSettings.toString());

        SiliconFlowChatService service = new SiliconFlowChatService(
                new ObjectMapper(),
                settingsService,
                "https://api.moonshot.cn/v1",
                "",
                "kimi-k2.5",
                "https://api.hunyuan.cloud.tencent.com/v1",
                "sk-hunyuan-from-env",
                "hunyuan-2.0-thinking-20251109",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "",
                "qwen3.5-plus",
                90,
                "https://api.deepseek.com",
                ""
        );

        boolean hasCredential = service.hasApiCredentialForModel("hunyuan-2.0-thinking-20251109", "");
        assertTrue(hasCredential);
    }

    @Test
    void shouldResolveQwenCredentialFromPersistedSettings() throws Exception {
        Path tempSettings = Files.createTempFile("llm-settings", ".json");
        SiliconFlowSettingsService settingsService = new SiliconFlowSettingsService(new ObjectMapper(), tempSettings.toString());
        settingsService.saveQwenApiKey("sk-qwen-from-settings");

        SiliconFlowChatService service = new SiliconFlowChatService(
                new ObjectMapper(),
                settingsService,
                "https://api.moonshot.cn/v1",
                "",
                "kimi-k2.5",
                "https://api.hunyuan.cloud.tencent.com/v1",
                "",
                "hunyuan-2.0-thinking-20251109",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "",
                "qwen3.5-plus",
                90,
                "https://api.deepseek.com",
                ""
        );

        boolean hasCredential = service.hasApiCredentialForModel("qwen3.5-plus", "");
        assertTrue(hasCredential);
    }
}
