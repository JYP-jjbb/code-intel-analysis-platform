package com.course.ideology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SiliconFlowChatServiceThinkingModeTest {

    @Test
    void shouldRouteDeepSeekToChatOutsideBatchModule() throws Exception {
        SiliconFlowChatService service = createService();

        Map<String, Object> deepSeekSingle = service.buildDebugDispatchPreview(
                "deepseek-ai/DeepSeek-V3.2",
                "nutera.single.candidate-generation.attempt-1"
        );
        Map<String, Object> deepSeekForcedChat = service.buildDebugDispatchPreview(
                "deepseek-reasoner",
                "code-review.project-audit"
        );

        assertEquals("deepseek_official", deepSeekSingle.get("provider"));
        assertEquals("deepseek-chat", deepSeekSingle.get("upstream_model"));
        assertEquals("chat", deepSeekSingle.get("mode_type"));
        assertEquals(false, deepSeekSingle.get("reasoning"));
        assertEquals(false, deepSeekSingle.get("deepseek_mode_forced"));

        assertEquals("deepseek_official", deepSeekForcedChat.get("provider"));
        assertEquals("deepseek-chat", deepSeekForcedChat.get("upstream_model"));
        assertEquals("chat", deepSeekForcedChat.get("mode_type"));
        assertEquals(false, deepSeekForcedChat.get("reasoning"));
        assertEquals(true, deepSeekForcedChat.get("deepseek_mode_forced"));
    }

    @Test
    void shouldForceReasonerInBatchModule() throws Exception {
        SiliconFlowChatService service = createService();

        Map<String, Object> deepSeekBatch = service.buildDebugDispatchPreview(
                "deepseek-chat",
                "nutera.batch.candidate-generation.attempt-2"
        );

        assertEquals("deepseek_official", deepSeekBatch.get("provider"));
        assertEquals("deepseek-reasoner", deepSeekBatch.get("upstream_model"));
        assertEquals("reasoner", deepSeekBatch.get("mode_type"));
        assertEquals(true, deepSeekBatch.get("reasoning"));
        assertEquals(true, deepSeekBatch.get("deepseek_mode_forced"));
    }

    @Test
    void shouldRouteMoonshotHunyuanAndQwenWithExpectedParams() throws Exception {
        SiliconFlowChatService service = createService();

        Map<String, Object> kimiLegacy = service.buildDebugDispatchPreview("Pro/moonshotai/Kimi-K2.5");
        Map<String, Object> kimiOfficial = service.buildDebugDispatchPreview("kimi-k2.5");
        Map<String, Object> hunyuan = service.buildDebugDispatchPreview("hunyuan-2.0-thinking-20251109");
        Map<String, Object> qwen = service.buildDebugDispatchPreview("qwen3.5-plus");

        assertEquals("moonshot_official", kimiLegacy.get("provider"));
        assertEquals("kimi-k2.5", kimiLegacy.get("upstream_model"));
        assertEquals(1.0, (Double) kimiLegacy.get("temperature"));
        assertEquals(false, kimiLegacy.get("enable_thinking"));
        assertEquals(false, kimiLegacy.get("reasoning"));

        assertEquals("moonshot_official", kimiOfficial.get("provider"));
        assertEquals("kimi-k2.5", kimiOfficial.get("upstream_model"));
        assertEquals(1.0, (Double) kimiOfficial.get("temperature"));
        assertEquals(false, kimiOfficial.get("enable_thinking"));
        assertEquals(false, kimiOfficial.get("reasoning"));

        assertEquals("hunyuan_official", hunyuan.get("provider"));
        assertEquals("hunyuan-2.0-thinking-20251109", hunyuan.get("upstream_model"));
        assertEquals(0.0, (Double) hunyuan.get("temperature"));
        assertEquals(false, hunyuan.get("enable_thinking"));
        assertEquals(false, hunyuan.get("reasoning"));

        assertEquals("qwen_official", qwen.get("provider"));
        assertEquals("qwen3.5-plus", qwen.get("upstream_model"));
        assertEquals(0.0, (Double) qwen.get("temperature"));
        assertEquals(true, qwen.get("enable_thinking"));
        assertEquals(true, qwen.get("reasoning"));
    }

    private SiliconFlowChatService createService() throws Exception {
        Path tempSettings = Files.createTempFile("moonshot-settings", ".json");
        SiliconFlowSettingsService settingsService = new SiliconFlowSettingsService(new ObjectMapper(), tempSettings.toString());
        return new SiliconFlowChatService(
                new ObjectMapper(),
                settingsService,
                "https://api.moonshot.cn/v1",
                "sk-moonshot-test",
                "kimi-k2.5",
                "https://api.hunyuan.cloud.tencent.com/v1",
                "sk-hunyuan-test",
                "hunyuan-2.0-thinking-20251109",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "sk-qwen-test",
                "qwen3.5-plus",
                90,
                "https://api.deepseek.com",
                "deepseek-chat",
                "deepseek-reasoner",
                "test-deepseek-key"
        );
    }
}
