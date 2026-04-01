package com.course.ideology.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SiliconFlowChatServiceThinkingModeTest {

    @Test
    void shouldRouteToMoonshotAndDeepSeekWithoutSiliconFlowSpecificParams() throws Exception {
        Path tempSettings = Files.createTempFile("moonshot-settings", ".json");
        SiliconFlowSettingsService settingsService = new SiliconFlowSettingsService(new ObjectMapper(), tempSettings.toString());
        SiliconFlowChatService service = new SiliconFlowChatService(
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
                "test-deepseek-key"
        );

        Map<String, Object> deepSeek = service.buildDebugDispatchPreview("deepseek-ai/DeepSeek-V3.2");
        Map<String, Object> kimiLegacy = service.buildDebugDispatchPreview("Pro/moonshotai/Kimi-K2.5");
        Map<String, Object> kimiOfficial = service.buildDebugDispatchPreview("kimi-k2.5");
        Map<String, Object> hunyuan = service.buildDebugDispatchPreview("hunyuan-2.0-thinking-20251109");
        Map<String, Object> qwen = service.buildDebugDispatchPreview("qwen3.5-plus");

        System.out.println("VERIFY model=deepseek-ai/DeepSeek-V3.2 params=" + deepSeek);
        System.out.println("VERIFY model=Pro/moonshotai/Kimi-K2.5 params=" + kimiLegacy);
        System.out.println("VERIFY model=kimi-k2.5 params=" + kimiOfficial);
        System.out.println("VERIFY model=hunyuan-2.0-thinking-20251109 params=" + hunyuan);
        System.out.println("VERIFY model=qwen3.5-plus params=" + qwen);

        assertEquals("deepseek_official", deepSeek.get("provider"));
        assertEquals("deepseek-chat", deepSeek.get("upstream_model"));
        assertEquals(0.0, (Double) deepSeek.get("temperature"));
        assertEquals(false, deepSeek.get("enable_thinking"));
        assertFalse(deepSeek.containsKey("reasoning"));

        assertEquals("moonshot_official", kimiLegacy.get("provider"));
        assertEquals("kimi-k2.5", kimiLegacy.get("upstream_model"));
        assertEquals(1.0, (Double) kimiLegacy.get("temperature"));
        assertEquals(false, kimiLegacy.get("enable_thinking"));
        assertFalse(kimiLegacy.containsKey("reasoning"));

        assertEquals("moonshot_official", kimiOfficial.get("provider"));
        assertEquals("kimi-k2.5", kimiOfficial.get("upstream_model"));
        assertEquals(1.0, (Double) kimiOfficial.get("temperature"));
        assertEquals(false, kimiOfficial.get("enable_thinking"));
        assertFalse(kimiOfficial.containsKey("reasoning"));

        assertEquals("hunyuan_official", hunyuan.get("provider"));
        assertEquals("hunyuan-2.0-thinking-20251109", hunyuan.get("upstream_model"));
        assertEquals(0.0, (Double) hunyuan.get("temperature"));
        assertEquals(false, hunyuan.get("enable_thinking"));
        assertFalse(hunyuan.containsKey("reasoning"));

        assertEquals("qwen_official", qwen.get("provider"));
        assertEquals("qwen3.5-plus", qwen.get("upstream_model"));
        assertEquals(0.0, (Double) qwen.get("temperature"));
        assertEquals(true, qwen.get("enable_thinking"));
        assertFalse(qwen.containsKey("reasoning"));
    }
}
