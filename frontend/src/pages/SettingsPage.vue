<template>
  <div class="settings-console-page">
    <div ref="heroRef">
      <el-card class="wb-card settings-hero-card" shadow="never">
        <div class="settings-hero-main">
          <div class="settings-hero-copy">
            <h2>系统设置</h2>
            <p>统一维护多模型供应商配置，支持 DeepSeek / Kimi / Hunyuan / Qwen。</p>
          </div>
        </div>
      </el-card>
    </div>

    <div ref="consoleRef">
      <el-card class="wb-card settings-console-card" shadow="never">
        <div class="settings-console-grid">
          <section ref="leftPanelRef" class="settings-left-panel">
            <div class="settings-panel-head">
              <h3>LLM 配置</h3>
              <p>通用字段按 provider 独立保存；API Key 留空时不会覆盖已保存值。</p>
            </div>

            <div class="settings-form-grid">
              <div class="settings-field">
                <label class="wb-field-label">供应商</label>
                <el-select v-model="selectedProvider" class="settings-control" @change="handleProviderChange">
                  <el-option v-for="item in providerOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </div>

              <div class="settings-field">
                <label class="wb-field-label">providerName</label>
                <el-input v-model="form.providerName" class="settings-control" />
              </div>

              <div class="settings-field">
                <label class="wb-field-label">modelName</label>
                <el-input v-model="form.modelName" class="settings-control" />
              </div>

              <div class="settings-field">
                <label class="wb-field-label">baseUrl</label>
                <el-input v-model="form.baseUrl" class="settings-control" />
              </div>

              <div class="settings-field settings-field-full">
                <label class="wb-field-label">apiKey</label>
                <el-input
                  v-model="form.apiKey"
                  class="settings-control"
                  :type="revealKey ? 'text' : 'password'"
                  placeholder="留空表示保留当前已保存 Key"
                >
                  <template #prefix>
                    <el-icon><Lock /></el-icon>
                  </template>
                  <template #suffix>
                    <button class="settings-visibility-btn" type="button" @click="toggleVisibility">
                      <el-icon v-if="revealKey"><Hide /></el-icon>
                      <el-icon v-else><View /></el-icon>
                    </button>
                  </template>
                </el-input>
              </div>

              <div class="settings-field settings-field-full settings-inline-row">
                <div class="settings-inline-field settings-switch-field">
                  <label class="wb-field-label">enableThinking</label>
                  <el-switch v-model="form.enableThinking" class="settings-unified-switch" />
                </div>
                <div class="settings-inline-field settings-switch-field">
                  <label class="wb-field-label">stream</label>
                  <el-switch v-model="form.stream" class="settings-unified-switch" />
                </div>
              </div>

              <div class="settings-field settings-field-full settings-triple-row">
                <div class="settings-inline-field">
                  <label class="wb-field-label">temperature</label>
                  <el-input-number v-model="form.temperature" class="settings-control" :step="0.1" :precision="2" />
                </div>
                <div class="settings-inline-field">
                  <label class="wb-field-label">maxTokens</label>
                  <el-input-number v-model="form.maxTokens" class="settings-control" :step="128" :min="0" />
                </div>
                <div class="settings-inline-field">
                  <label class="wb-field-label">timeout(s)</label>
                  <el-input-number v-model="form.timeout" class="settings-control" :step="5" :min="1" />
                </div>
              </div>

              <div class="settings-field settings-field-full">
                <label class="wb-field-label">extraConfig</label>
                <el-input
                  v-model="form.extraConfig"
                  class="settings-control settings-extra-control"
                  type="textarea"
                  :autosize="{ minRows: 1, maxRows: 2 }"
                />
              </div>
            </div>

            <div class="settings-actions">
              <el-button class="wb-primary-btn settings-primary-btn" type="primary" :loading="saving" @click="handleSave">
                保存配置
              </el-button>
              <el-button class="wb-soft-btn settings-soft-btn" :loading="loading" @click="handleRefresh">
                刷新状态
              </el-button>
            </div>
          </section>

          <aside ref="rightPanelRef" class="settings-right-panel">
            <div class="settings-top-card">
              <div class="settings-state-header">
                <div class="settings-state-title">状态总览</div>
                <span ref="statusPillRef" class="settings-state-pill" :class="statusToneClass">
                  <el-icon class="settings-state-pill-icon"><component :is="statusIcon" /></el-icon>
                  <span>{{ statusLabel }}</span>
                </span>
              </div>

              <div class="settings-core-grid">
                <div class="settings-core-item settings-animate-item" :ref="setStatusItemRef">
                  <dt>当前供应商</dt>
                  <dd>{{ selectedProviderLabel }}</dd>
                </div>
                <div class="settings-core-item settings-animate-item" :ref="setStatusItemRef">
                  <dt>调用模型</dt>
                  <dd>{{ form.modelName || "-" }}</dd>
                </div>
                <div class="settings-core-item settings-animate-item" :ref="setStatusItemRef">
                  <dt>Key 状态</dt>
                  <dd>{{ hasApiKey ? "已配置" : "未配置" }}</dd>
                </div>
                <div class="settings-core-item settings-animate-item" :ref="setStatusItemRef">
                  <dt>密钥摘要</dt>
                  <dd>{{ maskedApiKey || "--" }}</dd>
                </div>
              </div>
            </div>

            <dl class="settings-meta-list">
              <div class="settings-meta-item settings-animate-item" :ref="setStatusItemRef">
                <dt>Key 更新时间</dt>
                <dd>{{ apiKeyUpdatedAtDisplay }}</dd>
              </div>
              <div class="settings-meta-item settings-animate-item" :ref="setStatusItemRef">
                <dt>配置更新时间</dt>
                <dd>{{ configUpdatedAtDisplay }}</dd>
              </div>
              <div class="settings-meta-item settings-animate-item" :ref="setStatusItemRef">
                <dt>服务目标</dt>
                <dd>{{ serviceTarget || "-" }}</dd>
              </div>
              <div class="settings-meta-item settings-animate-item" :ref="setStatusItemRef">
                <dt>baseUrl</dt>
                <dd>{{ form.baseUrl || "-" }}</dd>
              </div>
              <div class="settings-meta-item settings-animate-item" :ref="setStatusItemRef">
                <dt>timeout(s)</dt>
                <dd>{{ form.timeout ?? "-" }}</dd>
              </div>
            </dl>
          </aside>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { CircleCheckFilled, Hide, InfoFilled, Lock, View, WarningFilled } from "@element-plus/icons-vue";
import gsap from "gsap";
import { fetchProviderSettings, saveProviderSettings } from "../api/settingsApi.js";

const providerOptions = [
  { label: "DeepSeek", value: "deepseek" },
  { label: "Hunyuan", value: "hunyuan" },
  { label: "Kimi", value: "kimi" },
  { label: "Qwen", value: "qwen" }
];

const heroRef = ref(null);
const consoleRef = ref(null);
const leftPanelRef = ref(null);
const rightPanelRef = ref(null);
const statusPillRef = ref(null);
const statusItemRefs = ref([]);

const selectedProvider = ref("deepseek");
const revealKey = ref(false);
const loading = ref(false);
const saving = ref(false);
const hasApiKey = ref(false);
const maskedApiKey = ref("");
const apiKeyUpdatedAt = ref("");
const configUpdatedAt = ref("");
const serviceTarget = ref("");
const statusLoaded = ref(false);
const statusError = ref("");

const form = reactive({
  providerName: "",
  modelName: "",
  apiKey: "",
  baseUrl: "",
  enableThinking: false,
  temperature: 0,
  maxTokens: null,
  stream: false,
  timeout: 90,
  extraConfig: ""
});

const setStatusItemRef = (el) => {
  if (!el) {
    return;
  }
  if (!statusItemRefs.value.includes(el)) {
    statusItemRefs.value.push(el);
  }
};

const selectedProviderLabel = computed(() => {
  return providerOptions.find((item) => item.value === selectedProvider.value)?.label || selectedProvider.value;
});

const statusLabel = computed(() => {
  if (statusError.value) return "状态异常";
  if (hasApiKey.value) return "已配置";
  if (statusLoaded.value) return "未配置";
  return "状态未知";
});

const statusToneClass = computed(() => {
  if (statusError.value) return "is-error";
  if (hasApiKey.value) return "is-ready";
  return "is-empty";
});

const statusIcon = computed(() => {
  if (statusError.value) return WarningFilled;
  if (hasApiKey.value) return CircleCheckFilled;
  return InfoFilled;
});

const formatLocalDateTime = (value) => {
  if (!value) return "--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
};

const apiKeyUpdatedAtDisplay = computed(() => formatLocalDateTime(apiKeyUpdatedAt.value));
const configUpdatedAtDisplay = computed(() => formatLocalDateTime(configUpdatedAt.value));

const applyResponse = (payload) => {
  form.providerName = payload?.providerName || selectedProviderLabel.value;
  form.modelName = payload?.modelName || "";
  form.baseUrl = payload?.baseUrl || "";
  form.enableThinking = Boolean(payload?.enableThinking);
  form.temperature = payload?.temperature ?? 0;
  form.maxTokens = payload?.maxTokens ?? null;
  form.stream = Boolean(payload?.stream);
  form.timeout = payload?.timeout ?? 90;
  form.extraConfig = payload?.extraConfig || "";
  form.apiKey = "";

  hasApiKey.value = Boolean(payload?.hasApiKey);
  maskedApiKey.value = payload?.maskedApiKey || "";
  apiKeyUpdatedAt.value = payload?.apiKeyUpdatedAt || "";
  configUpdatedAt.value = payload?.updatedAt || "";
  serviceTarget.value = payload?.serviceTarget || "";
  statusLoaded.value = true;
  statusError.value = "";
};

const loadProvider = async (providerId, { silent = false } = {}) => {
  loading.value = true;
  try {
    const response = await fetchProviderSettings(providerId);
    applyResponse(response);
    console.info("[settings] provider loaded", {
      provider: providerId,
      baseUrl: response?.baseUrl,
      modelName: response?.modelName,
      enableThinking: response?.enableThinking,
      hasApiKey: response?.hasApiKey
    });
  } catch (error) {
    statusError.value = error.message || "读取配置失败";
    if (!silent) {
      ElMessage.error(statusError.value);
    }
  } finally {
    loading.value = false;
  }
};

const handleProviderChange = async (value) => {
  await loadProvider(value, { silent: false });
};

const handleRefresh = async () => {
  await loadProvider(selectedProvider.value, { silent: false });
};

const handleSave = async () => {
  saving.value = true;
  try {
    const payload = {
      providerName: String(form.providerName || "").trim(),
      modelName: String(form.modelName || "").trim(),
      baseUrl: String(form.baseUrl || "").trim(),
      enableThinking: Boolean(form.enableThinking),
      temperature: form.temperature ?? 0,
      maxTokens: form.maxTokens,
      stream: Boolean(form.stream),
      timeout: form.timeout ?? 90,
      extraConfig: String(form.extraConfig || "").trim()
    };
    if (String(form.apiKey || "").trim()) {
      payload.apiKey = String(form.apiKey || "").trim();
    }
    const response = await saveProviderSettings(selectedProvider.value, payload);
    applyResponse(response);
    console.info("[settings] provider saved", {
      provider: selectedProvider.value,
      baseUrl: response?.baseUrl,
      modelName: response?.modelName,
      enableThinking: response?.enableThinking,
      hasApiKey: response?.hasApiKey
    });
    ElMessage.success("配置已保存");
  } catch (error) {
    statusError.value = error.message || "保存配置失败";
    ElMessage.error(statusError.value);
  } finally {
    saving.value = false;
  }
};

const toggleVisibility = () => {
  revealKey.value = !revealKey.value;
};

onMounted(async () => {
  await loadProvider(selectedProvider.value, { silent: true });
  await nextTick();
  const timeline = gsap.timeline({ defaults: { ease: "power2.out" } });
  timeline
    .fromTo(heroRef.value, { autoAlpha: 0, y: 10 }, { autoAlpha: 1, y: 0, duration: 0.36 }, 0)
    .fromTo(leftPanelRef.value, { autoAlpha: 0, y: 12 }, { autoAlpha: 1, y: 0, duration: 0.42 }, 0.08)
    .fromTo(rightPanelRef.value, { autoAlpha: 0, y: 12 }, { autoAlpha: 1, y: 0, duration: 0.42 }, 0.12);

  if (statusItemRefs.value.length) {
    gsap.fromTo(
      statusItemRefs.value,
      { autoAlpha: 0, y: 7 },
      { autoAlpha: 1, y: 0, duration: 0.32, ease: "power2.out", stagger: 0.05, delay: 0.2 }
    );
  }
  if (statusPillRef.value) {
    gsap.to(statusPillRef.value, {
      boxShadow: "0 0 0 1px rgba(44,107,255,0.3), 0 0 16px rgba(44,107,255,0.22)",
      duration: 1.8,
      repeat: -1,
      yoyo: true,
      ease: "sine.inOut"
    });
  }
});

onBeforeUnmount(() => {
  gsap.killTweensOf(heroRef.value);
  gsap.killTweensOf(consoleRef.value);
  gsap.killTweensOf(leftPanelRef.value);
  gsap.killTweensOf(rightPanelRef.value);
  gsap.killTweensOf(statusPillRef.value);
  if (statusItemRefs.value.length) {
    gsap.killTweensOf(statusItemRefs.value);
  }
});
</script>

<style scoped>
.settings-console-page {
  display: grid;
  gap: 8px;
}

.settings-hero-card,
.settings-console-card {
  border-radius: 24px;
}

.settings-hero-main {
  min-height: 86px;
  display: flex;
  align-items: center;
}

.settings-hero-copy h2 {
  margin: 0;
  font-size: 23px;
  line-height: 1.18;
  color: #1d4475;
}

.settings-hero-copy p {
  margin: 5px 0 0;
  color: #6c82a0;
  font-size: 12px;
  line-height: 1.5;
}

.settings-console-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(330px, 0.9fr);
  gap: 10px;
}

.settings-left-panel {
  border-radius: 20px;
  border: 1px solid rgba(44, 107, 255, 0.16);
  background: linear-gradient(164deg, rgba(249, 253, 255, 0.99), rgba(243, 249, 255, 0.94));
  padding: 10px 11px 11px;
}

.settings-panel-head h3 {
  margin: 0;
  font-size: 16px;
  color: #1f4d82;
}

.settings-panel-head p {
  margin: 4px 0 8px;
  color: #67809f;
  font-size: 11px;
  line-height: 1.45;
}

.settings-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.settings-field {
  display: grid;
  gap: 4px;
}

.settings-field-full {
  grid-column: 1 / -1;
}

.settings-inline-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.settings-triple-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.settings-inline-field {
  display: grid;
  gap: 4px;
}

.settings-switch-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid rgba(44, 107, 255, 0.17);
  border-radius: 12px;
  background: rgba(248, 251, 255, 0.92);
  padding: 6px 9px;
}

.settings-control {
  width: 100%;
}

.settings-control :deep(.el-input__wrapper),
.settings-control :deep(.el-textarea__inner),
.settings-control :deep(.el-select__wrapper),
.settings-control :deep(.el-input-number .el-input__wrapper) {
  border-radius: 12px;
  border: 1px solid rgba(44, 107, 255, 0.2);
  box-shadow: none;
  background: #f8fbff;
  min-height: 34px;
}

.settings-control :deep(.el-input-number) {
  width: 100%;
}

.settings-extra-control :deep(.el-textarea__inner) {
  min-height: 36px;
  line-height: 1.4;
}

.settings-unified-switch :deep(.el-switch) {
  --el-switch-on-color: #2c6bff;
  --el-switch-off-color: #c9d8f1;
}

.settings-unified-switch :deep(.el-switch__core) {
  min-width: 44px;
  height: 22px;
  border-radius: 999px;
}

.settings-visibility-btn {
  border: 0;
  background: transparent;
  color: #5f7ba1;
  display: inline-flex;
  cursor: pointer;
  padding: 0;
}

.settings-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.settings-right-panel {
  border-radius: 20px;
  border: 1px solid rgba(44, 107, 255, 0.18);
  background: linear-gradient(155deg, rgba(249, 253, 255, 0.99), rgba(242, 248, 255, 0.93));
  padding: 9px 10px 10px;
}

.settings-top-card {
  border-radius: 14px;
  border: 1px solid rgba(44, 107, 255, 0.22);
  background: linear-gradient(148deg, rgba(255, 255, 255, 0.94), rgba(239, 247, 255, 0.92));
  box-shadow: 0 8px 18px rgba(40, 95, 182, 0.08);
  padding: 8px;
}

.settings-state-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 7px;
}

.settings-state-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f4d82;
}

.settings-state-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  padding: 3px 10px;
  border: 1px solid rgba(44, 107, 255, 0.28);
  font-size: 12px;
  color: #2c5e98;
  background: linear-gradient(130deg, #f0f6ff, #e6f0ff);
}

.settings-state-pill.is-ready {
  color: #13643c;
  border-color: rgba(34, 164, 104, 0.33);
  background: linear-gradient(130deg, #ebf9f1, #e1f4e9);
}

.settings-state-pill.is-error {
  color: #bd3f54;
  border-color: rgba(230, 93, 105, 0.34);
  background: linear-gradient(130deg, #fff2f5, #ffe7ed);
}

.settings-core-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.settings-core-item {
  border-radius: 11px;
  border: 1px solid rgba(44, 107, 255, 0.14);
  background: rgba(255, 255, 255, 0.88);
  padding: 6px 8px;
}

.settings-core-item dt {
  font-size: 10px;
  color: #6880a0;
}

.settings-core-item dd {
  margin: 2px 0 0;
  font-size: 13px;
  line-height: 1.25;
  color: #254a79;
  font-weight: 600;
}

.settings-meta-list {
  margin: 8px 0 0;
  display: grid;
  gap: 6px;
}

.settings-meta-item {
  border: 1px solid rgba(44, 107, 255, 0.14);
  border-radius: 11px;
  background: rgba(255, 255, 255, 0.78);
  padding: 7px 9px;
  transition: box-shadow 180ms ease, border-color 180ms ease, transform 180ms ease;
}

.settings-meta-item:hover,
.settings-core-item:hover {
  border-color: rgba(44, 107, 255, 0.28);
  box-shadow: 0 6px 14px rgba(40, 95, 182, 0.1);
  transform: translateY(-1px);
}

.settings-meta-item dt {
  font-size: 10px;
  color: #6a819f;
}

.settings-meta-item dd {
  margin: 2px 0 0;
  font-size: 12px;
  color: #274d7d;
  line-height: 1.4;
}

@media (max-width: 1024px) {
  .settings-console-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .settings-form-grid {
    grid-template-columns: 1fr;
  }

  .settings-inline-row,
  .settings-triple-row,
  .settings-core-grid {
    grid-template-columns: 1fr;
  }
}
</style>
