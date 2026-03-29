<template>
  <div class="settings-console-page">
    <div ref="heroRef">
      <el-card class="wb-card settings-hero-card" shadow="never">
        <div class="settings-hero-main">
          <div class="settings-hero-copy">
            <h2>{{ text.pageTitle }}</h2>
            <p>{{ text.pageSubtitle }}</p>
          </div>

          <div class="settings-hero-art" aria-hidden="true">
            <svg viewBox="0 0 220 96" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect x="14" y="14" width="192" height="68" rx="16" class="hero-art-grid" />
              <path
                class="hero-art-line"
                d="M34 58C47 58 52 43 64 43C76 43 79 56 92 56C104 56 110 36 124 36C138 36 140 62 156 62C170 62 173 47 186 47"
              />
              <circle cx="64" cy="43" r="4" class="hero-art-node" />
              <circle cx="124" cy="36" r="4" class="hero-art-node" />
              <circle cx="156" cy="62" r="4" class="hero-art-node" />
              <path
                class="hero-art-shield"
                d="M109 22L126 29V42C126 52 119 60 109 64C99 60 92 52 92 42V29L109 22Z"
              />
              <path class="hero-art-shield-lock" d="M109 38C107 38 106 39 106 41V45H112V41C112 39 111 38 109 38Z" />
            </svg>
          </div>
        </div>
      </el-card>
    </div>

    <div ref="consoleRef">
      <el-card class="wb-card settings-console-card" shadow="never">
        <div class="settings-console-grid">
          <section class="settings-left-panel">
            <div class="settings-panel-head">
              <h3>{{ text.configTitle }}</h3>
              <p>{{ text.configSubtitle }}</p>
            </div>

            <div class="settings-key-row">
              <label class="wb-field-label settings-key-label">{{ text.keyLabel }}</label>
              <el-input
                v-model="apiKey"
                class="settings-key-input"
                :type="revealKey ? 'text' : 'password'"
                :placeholder="text.keyPlaceholder"
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

            <div class="settings-actions">
              <el-button class="wb-primary-btn settings-primary-btn" type="primary" :loading="saving" @click="handleSave">
                {{ text.saveButton }}
              </el-button>
              <el-button class="wb-soft-btn settings-soft-btn" :loading="loading" @click="handleRefresh">
                {{ text.refreshButton }}
              </el-button>
            </div>
          </section>

          <aside class="settings-right-panel">
            <div class="settings-state-header">
              <div class="settings-state-title">{{ text.statePanelTitle }}</div>
              <span class="settings-state-pill" :class="statusToneClass">
                <el-icon class="settings-state-pill-icon"><component :is="statusIcon" /></el-icon>
                <span>{{ statusLabel }}</span>
              </span>
            </div>

            <dl class="settings-meta-list">
              <div class="settings-meta-item">
                <dt>{{ text.currentStatusLabel }}</dt>
                <dd>{{ statusLabel }}</dd>
              </div>
              <div class="settings-meta-item">
                <dt>{{ text.maskedKeyLabel }}</dt>
                <dd>{{ maskedKeyDisplay }}</dd>
              </div>
              <div class="settings-meta-item">
                <dt>{{ text.updatedAtLabel }}</dt>
                <dd>{{ updatedAtDisplay }}</dd>
              </div>
              <div class="settings-meta-item">
                <dt>{{ text.scopeLabel }}</dt>
                <dd>{{ scopeDisplay }}</dd>
              </div>
            </dl>
          </aside>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { CircleCheckFilled, Hide, InfoFilled, Lock, View, WarningFilled } from "@element-plus/icons-vue";
import gsap from "gsap";
import { fetchSiliconFlowKeyStatus, saveSiliconFlowKey } from "../api/settingsApi.js";

const text = {
  pageTitle: "\u7cfb\u7edf\u8bbe\u7f6e",
  pageSubtitle: "\u7ba1\u7406\u6a21\u578b\u670d\u52a1\u8c03\u7528\u51ed\u636e\u4e0e\u8fd0\u884c\u914d\u7f6e",
  configTitle: "API \u914d\u7f6e",
  configSubtitle: "\u5b89\u5168\u7ef4\u62a4\u6a21\u578b\u8c03\u7528\u51ed\u636e\uff0c\u5e76\u4fdd\u6301\u914d\u7f6e\u72b6\u6001\u53ef\u89c2\u6d4b\u3002",
  keyLabel: "\u63a5\u53e3\u5bc6\u94a5",
  keyPlaceholder: "\u8bf7\u8f93\u5165\u63a5\u53e3\u5bc6\u94a5\uff0c\u4f8b\u5982 sk-...",
  saveButton: "\u4fdd\u5b58\u5bc6\u94a5",
  refreshButton: "\u5237\u65b0\u72b6\u6001",
  statePanelTitle: "\u72b6\u6001\u603b\u89c8",
  currentStatusLabel: "\u5f53\u524d\u72b6\u6001",
  maskedKeyLabel: "\u5bc6\u94a5\u6458\u8981",
  updatedAtLabel: "\u6700\u8fd1\u66f4\u65b0",
  scopeLabel: "\u4f7f\u7528\u8303\u56f4",
  scopeDefault: "SiliconFlow Chat Completions",
  statusConfigured: "\u5df2\u914d\u7f6e",
  statusMissing: "\u672a\u914d\u7f6e",
  statusError: "\u72b6\u6001\u5f02\u5e38",
  statusUnknown: "\u72b6\u6001\u672a\u77e5",
  saveSuccess: "\u5bc6\u94a5\u5df2\u4fdd\u5b58",
  saveFailed: "\u4fdd\u5b58\u5931\u8d25",
  loadFailed: "\u8bfb\u53d6\u914d\u7f6e\u5931\u8d25",
  keyRequired: "\u8bf7\u8f93\u5165\u63a5\u53e3\u5bc6\u94a5"
};

const heroRef = ref(null);
const consoleRef = ref(null);
const apiKey = ref("");
const revealKey = ref(false);
const loading = ref(false);
const saving = ref(false);

const hasApiKey = ref(false);
const maskedApiKey = ref("");
const updatedAt = ref("");
const serviceTarget = ref(text.scopeDefault);
const statusLoaded = ref(false);
const statusError = ref("");

const statusLabel = computed(() => {
  if (statusError.value) {
    return text.statusError;
  }
  if (hasApiKey.value) {
    return text.statusConfigured;
  }
  if (statusLoaded.value) {
    return text.statusMissing;
  }
  return text.statusUnknown;
});

const statusToneClass = computed(() => {
  if (statusError.value) {
    return "is-error";
  }
  if (hasApiKey.value) {
    return "is-ready";
  }
  return "is-empty";
});

const statusIcon = computed(() => {
  if (statusError.value) {
    return WarningFilled;
  }
  if (hasApiKey.value) {
    return CircleCheckFilled;
  }
  return InfoFilled;
});

const maskedKeyDisplay = computed(() => maskedApiKey.value || "--");

const formatLocalDateTime = (value) => {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const pad = (n) => String(n).padStart(2, "0");
  return [
    date.getFullYear(),
    "-",
    pad(date.getMonth() + 1),
    "-",
    pad(date.getDate()),
    " ",
    pad(date.getHours()),
    ":",
    pad(date.getMinutes()),
    ":",
    pad(date.getSeconds())
  ].join("");
};

const updatedAtDisplay = computed(() => {
  if (!updatedAt.value) {
    return "--";
  }
  return formatLocalDateTime(updatedAt.value);
});

const scopeDisplay = computed(() => serviceTarget.value || text.scopeDefault);

const normalizeStatusPayload = (response) => {
  if (!response || typeof response !== "object") {
    return null;
  }
  if (response.data && typeof response.data === "object") {
    return response.data;
  }
  return response;
};

const applyStatusResponse = (response) => {
  const payload = normalizeStatusPayload(response);
  if (!payload) {
    return;
  }
  hasApiKey.value = Boolean(payload.hasApiKey);
  maskedApiKey.value = payload.maskedApiKey || "";
  updatedAt.value = payload.updatedAt || payload.apiKeyUpdatedAt || "";
  serviceTarget.value = payload.serviceTarget || text.scopeDefault;
  statusLoaded.value = true;
  statusError.value = "";
};

const loadStatus = async ({ silent } = { silent: false }) => {
  loading.value = true;
  try {
    const response = await fetchSiliconFlowKeyStatus();
    applyStatusResponse(response);
  } catch (error) {
    statusError.value = error.message || text.loadFailed;
    statusLoaded.value = false;
    hasApiKey.value = false;
    maskedApiKey.value = "";
    updatedAt.value = "";
    if (!silent) {
      ElMessage.error(statusError.value);
    }
  } finally {
    loading.value = false;
  }
};

const handleRefresh = async () => {
  await loadStatus({ silent: false });
};

const handleSave = async () => {
  if (!apiKey.value.trim()) {
    ElMessage.warning(text.keyRequired);
    return;
  }

  saving.value = true;
  try {
    const response = await saveSiliconFlowKey(apiKey.value.trim());
    applyStatusResponse(response);
    await loadStatus({ silent: true });
    apiKey.value = "";
    ElMessage.success(text.saveSuccess);
  } catch (error) {
    statusError.value = error.message || text.saveFailed;
    ElMessage.error(statusError.value);
  } finally {
    saving.value = false;
  }
};

const toggleVisibility = () => {
  revealKey.value = !revealKey.value;
};

onMounted(() => {
  loadStatus({ silent: true });

  nextTick(() => {
    const timeline = gsap.timeline({ defaults: { ease: "power2.out" } });
    timeline
      .fromTo(heroRef.value, { autoAlpha: 0, y: 12 }, { autoAlpha: 1, y: 0, duration: 0.42 }, 0)
      .fromTo(consoleRef.value, { autoAlpha: 0, y: 14 }, { autoAlpha: 1, y: 0, duration: 0.48 }, 0.12);
  });
});
</script>

<style scoped>
.settings-console-page {
  display: grid;
  gap: 10px;
}

.settings-hero-card {
  border-radius: 24px;
}

.settings-hero-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 100px;
}

.settings-hero-copy h2 {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
  color: #1d4475;
}

.settings-hero-copy p {
  margin: 6px 0 0;
  color: #6c82a0;
  font-size: 13px;
  line-height: 1.56;
}

.settings-hero-art {
  width: 220px;
  max-width: 100%;
  opacity: 0.55;
}

.settings-hero-art svg {
  display: block;
  width: 100%;
  height: auto;
}

.hero-art-grid {
  stroke: rgba(44, 107, 255, 0.17);
  fill: rgba(244, 250, 255, 0.65);
  stroke-dasharray: 4 6;
}

.hero-art-line {
  stroke: rgba(44, 107, 255, 0.33);
  stroke-width: 1.8;
}

.hero-art-node {
  fill: rgba(44, 107, 255, 0.45);
}

.hero-art-shield {
  fill: rgba(44, 107, 255, 0.12);
  stroke: rgba(44, 107, 255, 0.26);
}

.hero-art-shield-lock {
  fill: rgba(44, 107, 255, 0.36);
}

.settings-console-card {
  border-radius: 24px;
}

.settings-console-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(300px, 0.95fr);
  gap: 10px;
}

.settings-left-panel {
  --settings-label-width: 82px;
  border-radius: 20px;
  border: 1px solid rgba(44, 107, 255, 0.14);
  background: linear-gradient(165deg, rgba(249, 253, 255, 0.98), rgba(244, 250, 255, 0.92));
  padding: 12px 13px 13px;
}

.settings-panel-head {
  margin-bottom: 9px;
}

.settings-panel-head h3 {
  margin: 0;
  font-size: 17px;
  color: #1f4d82;
}

.settings-panel-head p {
  margin: 4px 0 0;
  color: #67809f;
  font-size: 12px;
  line-height: 1.5;
}

.settings-key-row {
  display: grid;
  grid-template-columns: var(--settings-label-width) minmax(0, 1fr);
  gap: 10px;
  align-items: center;
}

.settings-key-label {
  margin: 0;
  color: #43668f;
}

.settings-key-input {
  width: 100%;
}

.settings-key-input :deep(.el-input__wrapper) {
  min-height: 38px;
  border-radius: 14px;
  border: 1px solid rgba(44, 107, 255, 0.18);
  background: #f8fcff;
  box-shadow: none;
}

.settings-key-input :deep(.el-input__wrapper.is-focus) {
  border-color: rgba(44, 107, 255, 0.4);
  box-shadow: 0 0 0 3px rgba(44, 107, 255, 0.1);
}

.settings-visibility-btn {
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #4a6f98;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.settings-visibility-btn:hover {
  background: rgba(44, 107, 255, 0.1);
}

.settings-actions {
  margin-top: 24px;
  margin-left: calc(var(--settings-label-width) + 10px);
  display: flex;
  align-items: center;
  gap: 20px;
}

.settings-primary-btn.el-button {
  min-width: 124px;
  height: 38px;
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, #4a9bff, #2f73ff);
  box-shadow: 0 9px 17px rgba(44, 107, 255, 0.17);
}

.settings-soft-btn.el-button {
  min-width: 112px;
  height: 38px;
  border-radius: 999px;
  border: 1px solid rgba(44, 107, 255, 0.24);
  background: rgba(44, 107, 255, 0.07);
  color: #2f5f95;
}

.settings-right-panel {
  border-radius: 20px;
  border: 1px solid rgba(44, 107, 255, 0.14);
  background: linear-gradient(160deg, rgba(247, 252, 255, 0.98), rgba(242, 249, 255, 0.92));
  padding: 11px 12px;
  display: grid;
  gap: 8px;
}

.settings-state-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.settings-state-title {
  font-size: 13px;
  font-weight: 600;
  color: #3e628c;
}

.settings-state-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  font-size: 12px;
  font-weight: 600;
}

.settings-state-pill-icon {
  font-size: 13px;
}

.settings-state-pill.is-ready {
  background: rgba(34, 189, 126, 0.12);
  border-color: rgba(34, 189, 126, 0.32);
  color: #1f7a52;
}

.settings-state-pill.is-empty {
  background: rgba(127, 147, 176, 0.12);
  border-color: rgba(127, 147, 176, 0.3);
  color: #516986;
}

.settings-state-pill.is-error {
  background: rgba(233, 108, 120, 0.1);
  border-color: rgba(233, 108, 120, 0.28);
  color: #964652;
}

.settings-meta-list {
  display: grid;
  gap: 6px;
  margin: 0;
}

.settings-meta-item {
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  gap: 7px;
  align-items: start;
  border-radius: 12px;
  border: 1px solid rgba(44, 107, 255, 0.1);
  background: rgba(250, 253, 255, 0.9);
  padding: 7px 8px;
}

.settings-meta-item dt {
  margin: 0;
  font-size: 12px;
  color: #7087a4;
}

.settings-meta-item dd {
  margin: 0;
  font-size: 12px;
  color: #2d517d;
  word-break: break-word;
}

@media (max-width: 1080px) {
  .settings-console-grid {
    grid-template-columns: 1fr;
  }

  .settings-hero-art {
    width: 180px;
  }
}

@media (max-width: 780px) {
  .settings-key-row {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .settings-actions {
    margin-left: 0;
  }

  .settings-hero-main {
    grid-template-columns: 1fr;
  }

  .settings-hero-art {
    justify-self: start;
  }
}
</style>
