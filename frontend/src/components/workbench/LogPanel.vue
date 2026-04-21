<template>
  <el-card
    class="wb-card wb-log-card"
    :class="{ 'is-learning-mode': mode === 'learning', 'is-collapsed': collapsed }"
    shadow="never"
  >
    <template #header>
      <div class="wb-card-head wb-card-head-split">
        <h3>{{ panelTitle }}</h3>
        <button
          v-if="mode === 'learning'"
          class="wb-collapse-btn"
          :class="{ 'is-collapsed': collapsed }"
          :title="collapsed ? '展开' : '收起'"
          @click="$emit('toggle-collapse')"
        >
          <el-icon><ArrowDown /></el-icon>
        </button>
      </div>
    </template>

    <template v-if="mode === 'learning'">
      <div class="wb-collapsible-wrap" :class="{ 'is-collapsed': collapsed }">
        <div class="wb-collapsible-inner">
          <div class="wb-block wb-learning-guide-block">
            <div class="wb-block-title">当前代码行讲解</div>
            <div class="wb-learning-line-card">
              <p class="wb-learning-line-title">
                第 {{ normalizedLineExplanation.lineNumber }} 行
                <code>{{ normalizedLineExplanation.lineText || "(空行)" }}</code>
              </p>
              <p>{{ normalizedLineExplanation.lineExplanation }}</p>
              <p><strong>语法点：</strong>{{ normalizedLineExplanation.syntaxPoint }}</p>
              <p><strong>常见错误：</strong>{{ normalizedLineExplanation.commonMistake }}</p>
            </div>
          </div>

          <div class="wb-block wb-learning-guide-block">
            <div class="wb-block-title">当前代码块讲解</div>
            <div class="wb-learning-line-card wb-learning-block-card">
              <p class="wb-learning-line-title wb-learning-block-title">
                <span class="wb-learning-block-chip">{{ normalizedBlockExplanation.blockTypeLabel }}</span>
                <span>{{ normalizedBlockExplanation.blockTitle }}</span>
                <span class="wb-learning-block-range">
                  第 {{ normalizedBlockExplanation.startLine }}-{{ normalizedBlockExplanation.endLine }} 行
                </span>
              </p>
              <p>{{ normalizedBlockExplanation.blockExplanation }}</p>
              <p>
                <strong>关键知识点：</strong>
                <span>{{ normalizedBlockExplanation.keyPointsText }}</span>
              </p>
              <p>
                <strong>常见错误：</strong>
                <span>{{ normalizedBlockExplanation.commonMistakesText }}</span>
              </p>
            </div>
          </div>

          <details class="wb-raw-details" v-if="logs">
            <summary>原始运行日志</summary>
            <pre class="wb-code-block wb-log-scroll">{{ logs }}</pre>
          </details>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="wb-block wb-block-log-only">
        <pre
          ref="logRef"
          class="wb-code-block wb-log-scroll"
          @scroll="handleScroll"
        >{{ logs || "执行日志将在任务运行后显示在这里" }}</pre>
      </div>
    </template>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { ArrowDown } from "@element-plus/icons-vue";

const props = defineProps({
  logs: {
    type: String,
    default: ""
  },
  mode: {
    type: String,
    default: "verification"
  },
  teachingSteps: {
    type: Array,
    default: () => []
  },
  lineExplanation: {
    type: Object,
    default: () => ({})
  },
  blockExplanation: {
    type: Object,
    default: () => ({})
  },
  collapsed: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(["toggle-collapse"]);

const panelTitle = computed(() => (props.mode === "learning" ? "代码速通" : "运行日志"));

const normalizedLineExplanation = computed(() => {
  const source = props.lineExplanation || {};
  return {
    lineNumber: Number(source.lineNumber || 1),
    lineText: String(source.lineText || ""),
    lineExplanation: String(source.lineExplanation || "点击左侧代码行后，这里会展示该行的作用。"),
    syntaxPoint: String(source.syntaxPoint || "暂未识别到特定语法点。"),
    commonMistake: String(source.commonMistake || "注意标点符号与缩进保持一致。")
  };
});

const normalizeBlockTypeLabel = (value) => {
  const type = String(value || "").toUpperCase();
  if (type === "IMPORT_BLOCK") return "导入区";
  if (type === "NAMESPACE_BLOCK") return "命名空间区";
  if (type === "MAIN_FUNCTION_BLOCK") return "主函数区";
  if (type === "FUNCTION_BLOCK") return "函数区";
  if (type === "LOOP_BLOCK") return "循环区";
  if (type === "CONDITION_BLOCK") return "条件区";
  if (type === "OUTPUT_BLOCK") return "输出区";
  if (type === "RETURN_BLOCK") return "返回区";
  if (type === "GLOBAL_BLOCK") return "整体逻辑";
  if (type === "LOGIC_BLOCK") return "逻辑块";
  return "代码块";
};

const normalizedBlockExplanation = computed(() => {
  const source = props.blockExplanation || {};
  const startLine = Math.max(1, Number(source.startLine || 1));
  const endLineRaw = Math.max(1, Number(source.endLine || startLine));
  const endLine = Math.max(startLine, endLineRaw);
  const keyPoints = Array.isArray(source.keyPoints) ? source.keyPoints.filter(Boolean) : [];
  const commonMistakes = Array.isArray(source.commonMistakes) ? source.commonMistakes.filter(Boolean) : [];
  return {
    startLine,
    endLine,
    blockTitle: String(source.blockTitle || "当前代码块"),
    blockTypeLabel: normalizeBlockTypeLabel(source.blockType),
    blockExplanation: String(source.blockExplanation || "点击左侧代码行后，这里会显示该行所属代码块的整体说明。"),
    keyPointsText: keyPoints.length > 0 ? keyPoints.join("；") : "建议结合上下文关注此代码块的数据流与控制流。",
    commonMistakesText: commonMistakes.length > 0 ? commonMistakes.join("；") : "只看单行而忽略块级上下文，容易误解代码真实行为。"
  };
});

const logRef = ref(null);
const autoStickToBottom = ref(true);

const isNearBottom = (element) => {
  if (!element) return true;
  const threshold = 24;
  return element.scrollTop + element.clientHeight >= element.scrollHeight - threshold;
};

const handleScroll = () => {
  if (!logRef.value) return;
  autoStickToBottom.value = isNearBottom(logRef.value);
};

const scrollToBottom = () => {
  if (!logRef.value) return;
  logRef.value.scrollTop = logRef.value.scrollHeight;
};

watch(
  () => props.logs,
  () => {
    if (props.mode !== "verification") {
      return;
    }
    nextTick(() => {
      if (autoStickToBottom.value) {
        scrollToBottom();
      }
    });
  }
);

onMounted(() => {
  if (props.mode !== "verification") {
    return;
  }
  nextTick(scrollToBottom);
});
</script>

<style scoped>
.wb-learning-block-card {
  gap: 8px;
}

.wb-learning-block-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.wb-learning-block-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  border: 1px solid rgba(44, 107, 255, 0.2);
  background: rgba(44, 107, 255, 0.1);
  color: #2a5f97;
  font-size: 11px;
  line-height: 1;
  padding: 4px 8px;
}

.wb-learning-block-range {
  color: #5a7395;
  font-size: 11px;
}
</style>
