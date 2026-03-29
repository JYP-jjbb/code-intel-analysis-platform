<template>
  <el-card class="wb-card wb-task-config-card" shadow="never">
    <template #header>
      <div class="wb-card-head">
        <h3>验证任务配置</h3>
      </div>
    </template>

    <div class="wb-config-toolbar">
      <div class="wb-config-chip wb-config-chip-upload">
        <span class="wb-config-chip-label">文件上传</span>
        <el-upload
          class="wb-chip-upload"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="(file) => $emit('file-change', file)"
        >
          <el-button class="wb-chip-upload-btn" size="small">
            <el-icon><UploadFilled /></el-icon>
            <span>选择文件</span>
          </el-button>
        </el-upload>
      </div>

      <div class="wb-config-chip">
        <span class="wb-config-chip-label">案例选择</span>
        <el-select
          v-model="form.benchmark"
          class="wb-chip-control"
          size="small"
          placeholder="选择案例"
          popper-class="wb-config-select-dropdown"
        >
          <el-option v-for="item in benchmarkOptions" :key="item.label" :label="item.label" :value="item.value" />
        </el-select>
      </div>

      <div class="wb-config-chip">
        <span class="wb-config-chip-label">模型配置</span>
        <el-select
          v-model="form.model"
          class="wb-chip-control"
          size="small"
          placeholder="选择模型"
          popper-class="wb-config-select-dropdown"
        >
          <el-option v-for="item in modelOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </div>

      <div class="wb-config-chip wb-config-chip-language" :class="{ 'is-language-locked': isLanguageLocked }">
        <span class="wb-config-chip-label">语言设置</span>
        <el-select
          v-model="form.language"
          class="wb-chip-control"
          size="small"
          placeholder="选择语言"
          :disabled="isLanguageLocked"
          popper-class="wb-config-select-dropdown"
        >
          <el-option v-for="item in languageOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <span v-if="isLanguageLocked" class="wb-config-chip-tip">由案例决定</span>
      </div>
    </div>

    <div class="wb-form-group wb-code-group">
      <label class="wb-field-label">代码输入</label>
      <div class="wb-code-editor">
        <pre ref="gutterRef" class="wb-code-gutter" aria-hidden="true">{{ lineNumbers }}</pre>
        <textarea
          v-model="form.code"
          class="wb-code-textarea"
          spellcheck="false"
          placeholder="输入或粘贴待验证的程序片段"
          @scroll="syncGutterScroll"
        />
      </div>
    </div>

    <div class="wb-action-row wb-main-actions">
      <el-button
        class="wb-primary-btn"
        :class="{
          'is-pause-btn': isRunningState,
          'is-resume-btn': runState === 'paused'
        }"
        :type="isRunningState ? 'danger' : runState === 'paused' ? 'warning' : 'primary'"
        @click="isRunningState ? $emit('pause') : $emit('submit')"
      >
        <el-icon><component :is="primaryIcon" /></el-icon>
        {{ primaryLabel }}
      </el-button>
      <el-checkbox v-model="form.batchMode" :disabled="isRunningState" class="wb-batch-toggle">批量处理</el-checkbox>
      <el-button class="wb-soft-btn" @click="$emit('reset')">
        <el-icon><Brush /></el-icon>
        清空代码
      </el-button>
    </div>
  </el-card>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import { Brush, Check, UploadFilled, VideoPause, VideoPlay } from "@element-plus/icons-vue";

const gutterRef = ref(null);

const modelOptions = [
  { label: "DeepSeek", value: "deepseek-ai/DeepSeek-V3.2" },
  { label: "Hunyuan", value: "tencent/Hunyuan-A13B-Instruct" },
  { label: "Kimi", value: "Pro/moonshotai/Kimi-K2.5" }
];

const benchmarkOptions = [
  { label: "None", value: "none" },
  { label: "ALL", value: "ALL" },
  { label: "Cft", value: "Cft" },
  { label: "C-Lit", value: "C-Lit" },
  { label: "Num", value: "Num" },
  { label: "R-15", value: "R-15" },
  { label: "NLA", value: "NLA" },
  { label: "NAdv", value: "NAdv" }
];

const languageOptions = [
  { label: "Python", value: "python" },
  { label: "Java", value: "java" },
  { label: "C", value: "c" },
  { label: "C++", value: "cpp" },
  { label: "Go", value: "go" }
];

const props = defineProps({
  form: {
    type: Object,
    required: true
  },
  submitting: {
    type: Boolean,
    default: false
  },
  runState: {
    type: String,
    default: "idle"
  }
});

defineEmits(["submit", "pause", "reset", "file-change"]);

const isLanguageLocked = computed(() => props.form.benchmark && props.form.benchmark !== "none");
const lastCustomLanguage = ref("python");
const isRunningState = computed(() => props.runState === "running" || props.runState === "pausing");

const primaryLabel = computed(() => {
  if (props.runState === "running") return "暂停验证";
  if (props.runState === "pausing") return "暂停中";
  if (props.runState === "paused") return "继续验证";
  return "启动验证";
});

const primaryIcon = computed(() => {
  if (props.runState === "running" || props.runState === "pausing") return VideoPause;
  if (props.runState === "paused") return VideoPlay;
  return Check;
});

watch(
  () => props.form.benchmark,
  (value, previous) => {
    const wasLocked = previous && previous !== "none";
    const nowLocked = value && value !== "none";

    if (nowLocked && props.form.language !== "java") {
      lastCustomLanguage.value = props.form.language || lastCustomLanguage.value;
      props.form.language = "java";
      return;
    }

    if (!nowLocked && wasLocked && props.form.language === "java") {
      props.form.language = lastCustomLanguage.value || "python";
    }
  },
  { immediate: true }
);

watch(
  () => props.form.language,
  (value) => {
    if (!isLanguageLocked.value && value) {
      lastCustomLanguage.value = value;
    }
  }
);

const lineNumbers = computed(() => {
  const lineCount = Math.max(1, String(props.form.code || "").split("\n").length);
  return Array.from({ length: lineCount }, (_, index) => index + 1).join("\n");
});

const syncGutterScroll = (event) => {
  if (!gutterRef.value) return;
  gutterRef.value.scrollTop = event.target.scrollTop;
};
</script>
