<template>
  <el-card class="wb-card wb-task-config-card" shadow="never">
    <template #header>
      <div class="wb-card-head">
        <div class="wb-task-head-row">
          <h3>验证任务配置</h3>
          <el-radio-group
            :model-value="mode"
            size="small"
            class="wb-mode-switcher"
            @update:model-value="(value) => $emit('update:mode', value)"
          >
            <el-radio-button label="learning">学习模式</el-radio-button>
            <el-radio-button label="verification">验证模式</el-radio-button>
          </el-radio-group>
        </div>
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
      <div class="wb-code-editor" :class="{ 'is-brace-mode': gutterBraces.length > 0 }">
        <div ref="gutterRef" class="wb-code-gutter" aria-hidden="true">
          <!-- Brace block indicators, absolutely positioned, scroll with the gutter -->
          <div v-if="gutterBraces.length > 0" class="wb-brace-overlay" aria-hidden="true">
            <div
              v-for="(brace, index) in gutterBraces"
              :key="index"
              class="wb-brace-item"
              :class="`wb-brace-depth-${brace.depth}`"
              :style="{ top: `${brace.top}px`, height: `${brace.height}px` }"
            />
          </div>
          <span
            v-for="line in lineNumbers"
            :key="line"
            class="wb-gutter-line"
            :class="{ 'is-active': line === selectedLineNumber }"
          >
            {{ line }}
          </span>
        </div>
        <textarea
          ref="textareaRef"
          v-model="form.code"
          class="wb-code-textarea"
          spellcheck="false"
          placeholder="输入或粘贴待验证的程序片段"
          @scroll="syncGutterScroll"
          @click="handleCursorActivity"
          @keyup="handleCursorActivity"
          @mouseup="handleCursorActivity"
        />
      </div>
    </div>

    <div class="wb-action-row wb-main-actions">
      <el-button
        class="wb-primary-btn"
        :class="{
          'is-pause-btn': shouldShowPauseAction,
          'is-resume-btn': runState === 'paused'
        }"
        :type="shouldShowPauseAction ? 'danger' : runState === 'paused' && mode === 'verification' ? 'warning' : 'primary'"
        @click="shouldShowPauseAction ? $emit('pause') : $emit('submit')"
      >
        <el-icon><component :is="primaryIcon" /></el-icon>
        {{ primaryLabel }}
      </el-button>
      <el-checkbox
        v-if="mode === 'verification'"
        v-model="form.batchMode"
        :disabled="isRunningState"
        class="wb-batch-toggle"
      >
        批量处理
      </el-checkbox>
      <el-button class="wb-soft-btn" @click="$emit('reset')">
        <el-icon><Brush /></el-icon>
        清空代码
      </el-button>
    </div>
  </el-card>
</template>

<script setup>
import { computed, nextTick, ref, watch } from "vue";
import { Brush, Check, UploadFilled, VideoPause, VideoPlay } from "@element-plus/icons-vue";

const gutterRef = ref(null);
const textareaRef = ref(null);
const selectedLineNumber = ref(1);

const modelOptions = [
  { label: "Kimi", value: "kimi-k2.5" },
  { label: "DeepSeek", value: "deepseek-ai/DeepSeek-V3.2" },
  { label: "Hunyuan", value: "hunyuan-2.0-thinking-20251109" },
  { label: "Qwen", value: "qwen3.5-plus" }
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
  },
  mode: {
    type: String,
    default: "learning"
  },
  externalSelectedLine: {
    type: Number,
    default: 1
  },
  codeBlocks: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(["submit", "pause", "reset", "file-change", "update:mode", "line-select"]);

const isLanguageLocked = computed(() => props.form.benchmark && props.form.benchmark !== "none");
const lastCustomLanguage = ref("python");
const isRunningState = computed(() => props.runState === "running" || props.runState === "pausing");
const shouldShowPauseAction = computed(() => props.mode === "verification" && isRunningState.value);

const primaryLabel = computed(() => {
  if (props.mode === "learning") {
    return props.submitting ? "讲解中" : "开始讲解";
  }
  if (props.runState === "running") return "暂停验证";
  if (props.runState === "pausing") return "暂停中";
  if (props.runState === "paused") return "继续验证";
  return "启动验证";
});

const primaryIcon = computed(() => {
  if (props.mode === "learning") {
    return VideoPlay;
  }
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
  return Array.from({ length: lineCount }, (_, index) => index + 1);
});

// Gutter brace indicators: computed from code block ranges (startLine / endLine).
// Each entry is {top, height, depth} in pixels, ready for inline style.
const BRACE_LINE_HEIGHT = 12 * 1.6; // matches CSS: font-size 12px, line-height 1.6
const BRACE_PADDING_TOP = 12; // matches CSS: padding-top 12px on .wb-code-gutter

const gutterBraces = computed(() => {
  if (props.mode !== "learning") return [];
  const blocks = Array.isArray(props.codeBlocks) ? props.codeBlocks : [];
  if (blocks.length === 0) return [];

  const totalLines = Math.max(1, String(props.form.code || "").split("\n").length);

  // Sanitise and sort: wider (outer) blocks first so depths are computed correctly
  const sorted = blocks
    .filter((b) => b && Number.isFinite(Number(b.startLine)) && Number.isFinite(Number(b.endLine)))
    .map((b) => ({
      startLine: Math.max(1, Math.min(totalLines, Number(b.startLine))),
      endLine: Math.max(1, Math.min(totalLines, Number(b.endLine)))
    }))
    .sort((a, b) => a.startLine - b.startLine || b.endLine - a.endLine);

  // Nesting depth: count how many earlier blocks fully contain this one
  const depths = sorted.map((block, i) => {
    let depth = 0;
    for (let j = 0; j < i; j++) {
      if (sorted[j].startLine <= block.startLine && sorted[j].endLine >= block.endLine) {
        depth++;
      }
    }
    return Math.min(depth, 3); // cap visual depth at 3
  });

  return sorted.map((block, i) => ({
    top: BRACE_PADDING_TOP + (block.startLine - 1) * BRACE_LINE_HEIGHT,
    height: Math.max(BRACE_LINE_HEIGHT, (block.endLine - block.startLine + 1) * BRACE_LINE_HEIGHT),
    depth: depths[i]
  }));
});

const syncGutterScroll = (event) => {
  if (!gutterRef.value) return;
  gutterRef.value.scrollTop = event.target.scrollTop;
};

const resolveLineFromCursor = () => {
  const textarea = textareaRef.value;
  const code = String(props.form.code || "");
  const lines = code.split("\n");
  if (!textarea) {
    return { lineNumber: 1, lineText: lines[0] || "", totalLines: lines.length };
  }
  const cursor = Number(textarea.selectionStart || 0);
  const prefix = code.slice(0, cursor);
  const lineNumber = Math.max(1, prefix.split("\n").length);
  const lineText = lines[lineNumber - 1] || "";
  return { lineNumber, lineText, totalLines: lines.length };
};

const emitLineSelection = () => {
  const payload = resolveLineFromCursor();
  selectedLineNumber.value = Math.min(payload.lineNumber, Math.max(1, payload.totalLines));
  emit("line-select", {
    lineNumber: selectedLineNumber.value,
    lineText: payload.lineText,
    code: String(props.form.code || "")
  });
};

const handleCursorActivity = () => {
  emitLineSelection();
};

const moveCursorToLine = (lineNumber) => {
  const textarea = textareaRef.value;
  if (!(textarea instanceof HTMLTextAreaElement)) {
    return;
  }
  const code = String(props.form.code || "");
  const rows = code.split("\n");
  const targetLine = Math.max(1, Math.min(rows.length || 1, Number(lineNumber || 1)));
  let start = 0;
  for (let i = 0; i < targetLine - 1; i += 1) {
    start += rows[i].length + 1;
  }
  const end = start + String(rows[targetLine - 1] || "").length;
  textarea.selectionStart = start;
  textarea.selectionEnd = end;

  const computedStyle = window.getComputedStyle(textarea);
  const lineHeightRaw = Number.parseFloat(computedStyle.lineHeight || "");
  const lineHeight = Number.isFinite(lineHeightRaw) && lineHeightRaw > 0 ? lineHeightRaw : 20;
  const paddingTop = Number.parseFloat(computedStyle.paddingTop || "0") || 0;
  const targetScrollTop = Math.max(0, (targetLine - 1) * lineHeight - paddingTop - lineHeight);
  textarea.scrollTop = targetScrollTop;
  if (gutterRef.value) {
    gutterRef.value.scrollTop = targetScrollTop;
  }
};

const applyExternalSelectedLine = (lineNumber) => {
  const lineCount = Math.max(1, String(props.form.code || "").split("\n").length);
  const normalized = Math.max(1, Math.min(lineCount, Number(lineNumber || 1)));
  if (selectedLineNumber.value === normalized) {
    return;
  }
  selectedLineNumber.value = normalized;
  nextTick(() => {
    moveCursorToLine(normalized);
    emitLineSelection();
  });
};

watch(
  () => props.form.code,
  (nextCode) => {
    const lineCount = Math.max(1, String(nextCode || "").split("\n").length);
    if (selectedLineNumber.value > lineCount) {
      selectedLineNumber.value = lineCount;
    }
    emitLineSelection();
  },
  { immediate: true }
);

watch(
  () => props.externalSelectedLine,
  (lineNumber) => {
    applyExternalSelectedLine(lineNumber);
  },
  { immediate: true }
);
</script>
