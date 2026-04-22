<template>
  <div class="nutera-workbench">
    <div class="nutera-grid">
      <section ref="operationRef" class="nutera-operation" data-nutera-left>
        <TaskConfigCard
          :form="form"
          :submitting="submitting"
          :run-state="primaryRunState"
          :mode="currentMode"
          :external-selected-line="activeEditorLine"
          :code-blocks="isLearningMode ? learning.codeBlocks : []"
          :learning-code-run-state="learningCodeRunState"
          :learning-code-runnable="isLearningCodeRunnable"
          @file-change="handleFileChange"
          @line-select="handleCodeLineSelect"
          @update:mode="handleModeChange"
          @submit="submitTask"
          @pause="pauseTask"
          @reset="resetForm"
          @run-code="handleLearningRunCode"
        />
      </section>

      <section
        ref="displayRef"
        class="nutera-display"
        :class="{ 'is-learning-mode': isLearningMode }"
        :style="displayGridStyle"
        data-nutera-right
      >
        <template v-if="isLearningMode">
          <LogPanel
            :logs="logs"
            mode="learning"
            :teaching-steps="learning.steps"
            :line-explanation="learningLineExplanation"
            :block-explanation="learningBlockExplanation"
            :collapsed="logCollapsed"
            @toggle-collapse="logCollapsed = !logCollapsed"
          />
          <ProgramOutputPanel
            :source-code="form.code"
            :language="form.language"
            :output="learning.programOutput"
            :collapsed="outputCollapsed"
            @toggle-collapse="outputCollapsed = !outputCollapsed"
          />
          <SummaryPanel
            mode="learning"
            :knowledge-points="learning.knowledgePoints"
            :common-mistakes="learning.commonMistakes"
            :collapsed="summaryCollapsed"
            @toggle-collapse="summaryCollapsed = !summaryCollapsed"
          />
        </template>
        <template v-else>
          <LogPanel
            :logs="logs"
            mode="verification"
            :collapsed="verificationLogCollapsed"
            :collapsible="true"
            @toggle-collapse="verificationLogCollapsed = !verificationLogCollapsed"
          />
          <SummaryPanel
            mode="verification"
            :collapsed="verificationSummaryCollapsed"
            :collapsible="true"
            :candidate-functions="result.candidateFunctions"
            :checker-status="result.checkerStatus"
            :checker-verdict="result.checkerVerdict"
            :checker-conclusion="result.checkerConclusion"
            :checker-message="result.checkerMessage"
            :single-attempt-count="result.attemptCount"
            :single-max-attempts="result.maxAttempts"
            :checker-counterexample="result.checkerCounterexample"
            :checker-raw-output="result.checkerRawOutput"
            :checker-feedback="result.checkerFeedback"
            :artifact-summary="result.artifactSummary"
            :verification-summary-data="result.verificationSummary"
            :verification-code="form.code"
            :verification-selected-line="verification.selectedLine"
            :batch-mode="form.batchMode || result.batchMode"
            :batch-progress="result.batchProgress"
            :batch-results="result.batchResults"
            :batch-result-path="result.batchResultPath"
            :selected-case-key="selectedBatchCaseKey"
            @update:selected-case-key="handleSelectedCaseKeyUpdate"
            @select-verification-line="handleVerificationLineSelectFromGraph"
            @toggle-collapse="verificationSummaryCollapsed = !verificationSummaryCollapsed"
          />
        </template>
      </section>
    </div>
    <LeaveTaskConfirmDialog
      v-model="leaveDialogVisible"
      title="确认离开当前任务？"
      message="当前批量验证仍在后台执行。离开页面后，任务不会中断；返回页面时可继续查看实时进度与结果。"
      cancel-text="留在本页"
      confirm-text="继续离开"
      @cancel="handleLeaveDialogCancel"
      @confirm="handleLeaveDialogConfirm"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, toRef, watch } from "vue";
import { ElMessage } from "element-plus";
import gsap from "gsap";
import { onBeforeRouteLeave } from "vue-router";
import TaskConfigCard from "../components/workbench/TaskConfigCard.vue";
import LogPanel from "../components/workbench/LogPanel.vue";
import ProgramOutputPanel from "../components/workbench/ProgramOutputPanel.vue";
import SummaryPanel from "../components/workbench/SummaryPanel.vue";
import LeaveTaskConfirmDialog from "../components/workbench/LeaveTaskConfirmDialog.vue";
import {
  buildVerificationSummaryGraph,
  explainLearningCode,
  fetchBatchStatus,
  generateRankingFunction,
  loadCaseSource,
  pauseBatchTask,
  runLearningCodeSnippet,
  startBatchTask
} from "../api/nuteraApi.js";
import { useNuteraRuntimeStore } from "../stores/nuteraRuntimeStore.js";

const operationRef = ref(null);
const displayRef = ref(null);
const runtimeStore = useNuteraRuntimeStore();
runtimeStore.hydrate();
const runtimeState = runtimeStore.state;

const form = runtimeState.form;
const result = runtimeState.result;
const caseSource = runtimeState.caseSource;
const selectedFileName = toRef(runtimeState, "selectedFileName");
const logs = toRef(runtimeState, "logs");
const submitting = toRef(runtimeState, "submitting");
const activeBatchTaskId = toRef(runtimeState, "activeBatchTaskId");
const lastBatchCompletedCases = toRef(runtimeState, "lastBatchCompletedCases");
const selectedBatchCaseKey = toRef(runtimeState, "selectedBatchCaseKey");
const verification = runtimeState.verification;
const learning = runtimeState.learning;
let caseLoadToken = 0;

let batchPollingTimer = null;
let batchPollingInFlight = false;
let singleRunAbortController = null;
let batchCaseSyncToken = 0;
let batchCaseSyncInFlightKey = "";
let batchCodeBoundKey = "";
const batchCaseSourceCache = new Map();
let persistDebounceTimer = null;
const leaveDialogVisible = ref(false);
let pendingRouteLeaveNext = null;
let bypassRouteLeaveGuardOnce = false;
const leavePromptMessage = "当前批量处理尚未完成，离开页面后任务会继续在后台执行，是否继续离开？";
const primaryRunState = computed(() => {
  const checkerStatus = String(result.checkerStatus || "").toUpperCase();
  if (submitting.value) {
    return checkerStatus === "PAUSING" ? "pausing" : "running";
  }
  if (checkerStatus === "PAUSED") {
    return "paused";
  }
  return "idle";
});

const currentMode = computed({
  get: () => (runtimeState.ui.mode === "verification" ? "verification" : "learning"),
  set: (value) => {
    runtimeState.ui.mode = value === "verification" ? "verification" : "learning";
  }
});

const isLearningMode = computed(() => currentMode.value === "learning");

const learningCodeRunState = ref("idle");
const LEARNING_RUN_SUPPORTED_LANGS = new Set(["python", "java", "c", "cpp", "go"]);
const isLearningCodeRunnable = computed(() =>
  LEARNING_RUN_SUPPORTED_LANGS.has(String(form.language || "").toLowerCase())
);

watch(isLearningMode, (on) => {
  if (!on) {
    learningCodeRunState.value = "idle";
  }
});

// ── Collapse state for the three learning-mode right-column cards ──────────
const logCollapsed = ref(false);
const outputCollapsed = ref(false);
const summaryCollapsed = ref(false);
const verificationLogCollapsed = ref(false);
const verificationSummaryCollapsed = ref(false);

/**
 * Dynamic grid-template-rows for the .nutera-display column in learning mode.
 * Priority: log (42fr) > output (18fr) > summary (40fr).
 * When a card collapses its row becomes "auto" (header-only height).
 * The freed fr units are reassigned to the highest-priority expanded card.
 */
const learningDisplayGridRows = computed(() => {
  const l = !logCollapsed.value;
  const o = !outputCollapsed.value;
  const s = !summaryCollapsed.value;

  let logFr = l ? 42 : 0;
  let outputFr = o ? 18 : 0;
  let summaryFr = s ? 40 : 0;

  const freed = (l ? 0 : 42) + (o ? 0 : 18) + (s ? 0 : 40);
  if (freed > 0) {
    if (l) { logFr += freed; }
    else if (o) { outputFr += freed; }
    else if (s) { summaryFr += freed; }
  }

  const row = (expanded, fr) => expanded ? `minmax(0, ${fr}fr)` : "auto";
  return `${row(l, logFr)} ${row(o, outputFr)} ${row(s, summaryFr)}`;
});

const verificationDisplayGridRows = computed(() => {
  const l = !verificationLogCollapsed.value;
  const s = !verificationSummaryCollapsed.value;

  if (l && s) {
    return "minmax(0, 35fr) minmax(0, 65fr)";
  }
  if (!l && !s) {
    return "auto auto";
  }
  if (l) {
    return "minmax(0, 1fr) auto";
  }
  return "auto minmax(0, 1fr)";
});

const displayGridStyle = computed(() => ({
  gridTemplateRows: isLearningMode.value
    ? learningDisplayGridRows.value
    : verificationDisplayGridRows.value
}));

const learningLineExplanation = computed(() => ({
  lineNumber: Number(learning.selectedLine || 1),
  lineText: String(learning.lineText || ""),
  lineExplanation: String(learning.lineExplanation || ""),
  syntaxPoint: String(learning.syntaxPoint || ""),
  commonMistake: String(learning.commonMistake || "")
}));

const learningBlockExplanation = computed(() => {
  const source = learning.selectedBlock || {};
  const startLine = Math.max(1, Number(source.startLine || learning.selectedLine || 1));
  const endLineRaw = Math.max(1, Number(source.endLine || startLine));
  return {
    startLine,
    endLine: Math.max(startLine, endLineRaw),
    blockTitle: String(source.blockTitle || "当前代码块"),
    blockType: String(source.blockType || "LOGIC_BLOCK"),
    blockExplanation: String(source.blockExplanation || "点击左侧代码行后，将展示该行所属代码块的整体说明。"),
    keyPoints: Array.isArray(source.keyPoints) ? source.keyPoints.map((item) => String(item || "")).filter(Boolean) : [],
    commonMistakes: Array.isArray(source.commonMistakes)
      ? source.commonMistakes.map((item) => String(item || "")).filter(Boolean)
      : []
  };
});

const activeEditorLine = computed(() => {
  if (isLearningMode.value) {
    return Math.max(1, Number(learning.selectedLine || 1));
  }
  return Math.max(1, Number(verification.selectedLine || 1));
});

const nowLabel = () => new Date().toLocaleString("zh-CN", { hour12: false });

const appendLog = (message) => {
  const line = `[${nowLabel()}] ${message}`;
  logs.value = logs.value ? `${logs.value}\n${line}` : line;
};

const resetLearningPanels = () => {
  learningCodeRunState.value = "idle";
  learning.selectedLine = 1;
  learning.lineText = "";
  learning.lineExplanation = "";
  learning.syntaxPoint = "";
  learning.commonMistake = "";
  learning.lineExplanations = [];
  learning.codeBlocks = [];
  learning.selectedBlock = null;
  learning.explainedCodeFingerprint = "";
  learning.steps = [];
  learning.programOutput = "";
  learning.knowledgePoints = [];
  learning.commonMistakes = [];
};

const resolveLanguageLabel = () => {
  const normalized = String(form.language || "").toLowerCase();
  if (normalized === "cpp" || normalized === "c++") return "C++";
  if (normalized === "c") return "C";
  if (normalized === "java") return "Java";
  if (normalized === "go") return "Go";
  return "Python";
};

const buildKnowledgePoints = (code) => {
  const points = [];
  const source = String(code || "");
  const languageLabel = resolveLanguageLabel();
  points.push(`当前示例采用 ${languageLabel} 语法，建议先从入口函数与主流程阅读。`);
  if (/\b(if|else)\b/.test(source)) {
    points.push("代码包含条件分支，需要重点理解条件成立与不成立时的执行路径。");
  }
  if (/\b(for|while)\b/.test(source)) {
    points.push("代码存在循环结构，建议同步追踪循环变量和终止条件。");
  }
  if (/(print\s*\(|cout\s*<<|console\.log|System\.out\.print)/.test(source)) {
    points.push("包含输出语句，可通过输出结果反推执行顺序和变量状态。");
  }
  if (/\b(return)\b/.test(source)) {
    points.push("注意返回语句位置，返回时函数会立即结束后续语句。");
  }
  return points.slice(0, 6);
};

const buildCommonMistakes = (code) => {
  const mistakes = [
    "漏写分号、括号或冒号会导致语句结构不完整。",
    "变量先使用后初始化，容易出现运行结果异常。",
    "循环终止条件写错会导致死循环或少执行一次。"
  ];
  const source = String(code || "");
  if (/\b(if|for|while)\b/.test(source)) {
    mistakes.push("条件判断中把赋值符号写成比较符号是常见错误。");
  }
  if (/(cout\s*<<|print\s*\(|console\.log|System\.out\.print)/.test(source)) {
    mistakes.push("输出语句中字符串引号不匹配会直接触发语法错误。");
  }
  return mistakes.slice(0, 6);
};

const buildLearningSteps = (code) => {
  const steps = [];
  const lines = String(code || "").split("\n");
  const activeLines = lines
    .map((line, index) => ({ line: String(line || "").trim(), lineNumber: index + 1 }))
    .filter((item) => item.line);
  if (activeLines.length === 0) {
    return ["请先输入代码，然后点击“开始讲解”生成学习步骤。"];
  }
  const preview = activeLines.slice(0, 6);
  preview.forEach((item) => {
    steps.push(`第 ${item.lineNumber} 行：${item.line}`);
  });
  if (activeLines.length > preview.length) {
    steps.push("其余语句可通过点击代码行查看逐行解释。");
  }
  return steps;
};

const buildLineExplanation = (lineNumber, lineText) => {
  const text = String(lineText || "").trim();
  if (!text) {
    return {
      lineNumber,
      lineText: "",
      lineExplanation: "这是空行或仅包含空白字符，主要用于代码排版与阅读分组。",
      syntaxPoint: "空行不参与语义执行。",
      commonMistake: "过度压缩空行会降低代码可读性。"
    };
  }
  if (/^\s*#include|^\s*import|^\s*using\b/.test(text)) {
    return {
      lineNumber,
      lineText: text,
      lineExplanation: "该行用于引入外部库或命名空间，为后续语句提供依赖。",
      syntaxPoint: "模块/包导入语法。",
      commonMistake: "导入路径或类名拼写错误会导致编译失败。"
    };
  }
  if (/\b(if|else if|else)\b/.test(text)) {
    return {
      lineNumber,
      lineText: text,
      lineExplanation: "该行控制分支选择，不同条件会进入不同代码路径。",
      syntaxPoint: "条件判断与布尔表达式。",
      commonMistake: "条件表达式边界遗漏，导致分支命中不符合预期。"
    };
  }
  if (/\b(for|while)\b/.test(text)) {
    return {
      lineNumber,
      lineText: text,
      lineExplanation: "该行定义循环结构，程序会重复执行循环体直到条件不满足。",
      syntaxPoint: "循环控制与终止条件。",
      commonMistake: "循环变量更新遗漏，可能导致无限循环。"
    };
  }
  if (/(cout\s*<<|print\s*\(|console\.log|System\.out\.print)/.test(text)) {
    return {
      lineNumber,
      lineText: text,
      lineExplanation: "该行用于输出内容，便于观察程序执行结果。",
      syntaxPoint: "输出语句与流操作符。",
      commonMistake: "输出语句末尾标点缺失，或字符串引号不成对。"
    };
  }
  if (/\b(return)\b/.test(text)) {
    return {
      lineNumber,
      lineText: text,
      lineExplanation: "该行会将值返回给调用方，并结束当前函数执行。",
      syntaxPoint: "函数返回值与控制流终止。",
      commonMistake: "返回值类型与函数声明不一致。"
    };
  }
  if (/(=|\+=|-=|\*=|\/=)/.test(text)) {
    return {
      lineNumber,
      lineText: text,
      lineExplanation: "该行用于变量赋值或更新，会直接影响后续计算结果。",
      syntaxPoint: "赋值语句与表达式求值。",
      commonMistake: "将比较运算符与赋值运算符混用。"
    };
  }
  return {
    lineNumber,
    lineText: text,
    lineExplanation: "该行参与当前程序逻辑，请结合上下文观察它对数据流的影响。",
    syntaxPoint: "基础语句结构。",
    commonMistake: "忽略上下文依赖会导致理解偏差。"
  };
};

const computeCodeFingerprint = (code) => {
  const source = String(code || "");
  let hash = 0;
  for (let i = 0; i < source.length; i += 1) {
    hash = (hash * 31 + source.charCodeAt(i)) >>> 0;
  }
  return `${source.length}:${hash}`;
};

const resolveLineTextByNumber = (code, lineNumber) => {
  const lines = String(code || "").split("\n");
  const normalizedLine = Math.max(1, Number(lineNumber || 1));
  return String(lines[normalizedLine - 1] || "");
};

const normalizeLearningLineExplanations = (value, totalLines) => {
  const source = Array.isArray(value) ? value : [];
  const rows = [];
  source.forEach((item) => {
    const lineNumber = Math.max(1, Number(item?.lineNumber ?? item?.line_number ?? item?.line ?? 0));
    if (totalLines > 0 && lineNumber > totalLines) {
      return;
    }
    rows.push({
      lineNumber,
      lineText: String(item?.lineText ?? item?.line_text ?? item?.code ?? ""),
      lineExplanation: String(item?.lineExplanation ?? item?.line_explanation ?? item?.explanation ?? ""),
      syntaxPoint: String(item?.syntaxPoint ?? item?.syntax_point ?? ""),
      commonMistake: String(item?.commonMistake ?? item?.common_mistake ?? "")
    });
  });
  return rows;
};

const normalizeLearningCodeBlock = (item, totalLines) => {
  if (!item || typeof item !== "object") {
    return null;
  }
  const startRaw = Number(item.startLine ?? item.start_line ?? item.lineStart ?? item.line_start ?? 0);
  const endRaw = Number(item.endLine ?? item.end_line ?? item.lineEnd ?? item.line_end ?? startRaw);
  if (!Number.isFinite(startRaw) || startRaw <= 0) {
    return null;
  }
  const startLine = Math.max(1, Math.floor(startRaw));
  const maxLine = Math.max(1, totalLines || startLine);
  const endLine = Math.max(startLine, Math.min(maxLine, Math.floor(Number.isFinite(endRaw) ? endRaw : startLine)));
  return {
    startLine,
    endLine,
    blockTitle: String(item.blockTitle ?? item.block_title ?? item.title ?? `代码块 ${startLine}-${endLine}`),
    blockType: String(item.blockType ?? item.block_type ?? item.type ?? "LOGIC_BLOCK"),
    blockExplanation: String(item.blockExplanation ?? item.block_explanation ?? item.explanation ?? ""),
    keyPoints: Array.isArray(item.keyPoints ?? item.key_points)
      ? (item.keyPoints ?? item.key_points).map((row) => String(row || "")).filter(Boolean)
      : [],
    commonMistakes: Array.isArray(item.commonMistakes ?? item.common_mistakes)
      ? (item.commonMistakes ?? item.common_mistakes).map((row) => String(row || "")).filter(Boolean)
      : []
  };
};

const normalizeLearningCodeBlocks = (value, totalLines) => {
  const source = Array.isArray(value) ? value : [];
  const rows = source
    .map((item) => normalizeLearningCodeBlock(item, totalLines))
    .filter(Boolean)
    .sort((a, b) => (a.startLine - b.startLine) || (a.endLine - b.endLine));
  return rows;
};

const buildFallbackBlockExplanation = (lineNumber) => ({
  startLine: lineNumber,
  endLine: lineNumber,
  blockTitle: "当前代码片段",
  blockType: "LINE_BLOCK",
  blockExplanation: "暂未生成代码块讲解。点击“开始讲解”后，将基于整段代码生成结构化分块说明。",
  keyPoints: ["可先查看当前代码行讲解，再启动“开始讲解”获取块级解释。"],
  commonMistakes: ["仅看单行容易忽略上下文依赖，建议结合代码块整体理解。"]
});

const resolveBlockByLine = (blocks, lineNumber, preferredBlock = null) => {
  const normalizedLine = Math.max(1, Number(lineNumber || 1));
  const sourceBlocks = Array.isArray(blocks) ? blocks : [];
  const normalizedPreferred = normalizeLearningCodeBlock(preferredBlock, sourceBlocks.length || normalizedLine);
  if (
    normalizedPreferred &&
    normalizedLine >= normalizedPreferred.startLine &&
    normalizedLine <= normalizedPreferred.endLine
  ) {
    return normalizedPreferred;
  }

  let matched = null;
  let bestSpan = Number.POSITIVE_INFINITY;
  sourceBlocks.forEach((block) => {
    if (normalizedLine < block.startLine || normalizedLine > block.endLine) {
      return;
    }
    const span = Math.max(1, block.endLine - block.startLine + 1);
    if (span < bestSpan) {
      bestSpan = span;
      matched = block;
    }
  });
  if (matched) {
    return matched;
  }
  if (normalizedPreferred) {
    return normalizedPreferred;
  }
  return sourceBlocks[0] || null;
};

const applyLearningSelection = (lineNumber, lineText, { preferredBlock = null } = {}) => {
  const normalizedLine = Math.max(1, Number(lineNumber || 1));
  const resolvedLineText = String(lineText || resolveLineTextByNumber(form.code, normalizedLine));
  const matchedLineExplanation = Array.isArray(learning.lineExplanations)
    ? learning.lineExplanations.find((item) => Number(item?.lineNumber) === normalizedLine)
    : null;
  const fallbackLineExplanation = buildLineExplanation(normalizedLine, resolvedLineText);
  const lineExplanation = matchedLineExplanation
    ? {
      lineNumber: normalizedLine,
      lineText: String(matchedLineExplanation.lineText || resolvedLineText),
      lineExplanation: String(matchedLineExplanation.lineExplanation || fallbackLineExplanation.lineExplanation),
      syntaxPoint: String(matchedLineExplanation.syntaxPoint || fallbackLineExplanation.syntaxPoint),
      commonMistake: String(matchedLineExplanation.commonMistake || fallbackLineExplanation.commonMistake)
    }
    : fallbackLineExplanation;

  learning.selectedLine = normalizedLine;
  learning.lineText = lineExplanation.lineText;
  learning.lineExplanation = lineExplanation.lineExplanation;
  learning.syntaxPoint = lineExplanation.syntaxPoint;
  learning.commonMistake = lineExplanation.commonMistake;

  const matchedBlock = resolveBlockByLine(learning.codeBlocks, normalizedLine, preferredBlock);
  learning.selectedBlock = matchedBlock || buildFallbackBlockExplanation(normalizedLine);
};

const hydrateLearningFromCode = (code, { keepLineSelection = true } = {}) => {
  const source = String(code || "");
  const lines = source.split("\n");
  const maxLine = Math.max(1, lines.length);
  const nextFingerprint = computeCodeFingerprint(source);
  if (
    learning.explainedCodeFingerprint &&
    learning.explainedCodeFingerprint !== nextFingerprint
  ) {
    learning.lineExplanations = [];
    learning.codeBlocks = [];
    learning.selectedBlock = null;
    learning.explainedCodeFingerprint = "";
  }

  learning.steps = buildLearningSteps(source);
  learning.programOutput = "";
  learning.knowledgePoints = buildKnowledgePoints(source);
  learning.commonMistakes = buildCommonMistakes(source);

  const targetLine = keepLineSelection
    ? Math.max(1, Math.min(maxLine, Number(learning.selectedLine || 1)))
    : 1;
  const targetLineText = String(lines[targetLine - 1] || "");
  applyLearningSelection(targetLine, targetLineText);
};

const applyLearningExplainResponse = (response, lineNumber, lineText) => {
  const sourceCode = String(form.code || "");
  const totalLines = Math.max(1, sourceCode.split("\n").length);
  const lineExplanations = normalizeLearningLineExplanations(
    response?.line_explanations ?? response?.lineExplanations,
    totalLines
  );
  const codeBlocks = normalizeLearningCodeBlocks(
    response?.code_blocks ?? response?.codeBlocks,
    totalLines
  );
  const selectedBlock = normalizeLearningCodeBlock(
    response?.selected_block ?? response?.selectedBlock,
    totalLines
  );

  learning.lineExplanations = lineExplanations;
  learning.codeBlocks = codeBlocks;
  learning.explainedCodeFingerprint = computeCodeFingerprint(sourceCode);
  applyLearningSelection(lineNumber, lineText, { preferredBlock: selectedBlock });
};

const handleCodeLineSelect = (payload) => {
  const lineNumber = Math.max(1, Number(payload?.lineNumber || 1));
  const lineText = String(payload?.lineText || "");
  if (isLearningMode.value) {
    applyLearningSelection(lineNumber, lineText);
    return;
  }
  verification.selectedLine = lineNumber;
  verification.lineText = lineText || resolveLineTextByNumber(form.code, lineNumber);
};

const handleVerificationLineSelectFromGraph = (lineNumber) => {
  const normalizedLine = Math.max(1, Number(lineNumber || 1));
  verification.selectedLine = normalizedLine;
  verification.lineText = resolveLineTextByNumber(form.code, normalizedLine);
};

const buildVerificationSummaryFallback = ({
  candidateFunction = "",
  checkerStatus = "",
  checkerVerdict = "",
  checkerConclusion = "",
  checkerMessage = "",
  selectedLine = 1
} = {}) => {
  const line = Math.max(1, Number(selectedLine || 1));
  const codeLine = resolveLineTextByNumber(form.code, line);
  return {
    status: "FALLBACK",
    message: "后端图摘要暂不可用，已启用前端占位结构图。",
    summaryText: [
      `验证状态: ${checkerVerdict || checkerConclusion || checkerStatus || "UNKNOWN"}`,
      `候选函数: ${candidateFunction || "（空）"}`,
      "说明: 当前为前端兜底结构图，可继续查看代码切片与结论说明。"
    ].join("\n"),
    verificationStatus: checkerVerdict || checkerConclusion || checkerStatus || "NOT_PROVED",
    candidateFunction,
    focusLines: [line],
    graph: {
      nodes: [
        {
          id: "fallback-selected",
          label: codeLine || "当前代码行",
          type: "statement",
          lineStart: line,
          lineEnd: line,
          status: "focus",
          explanation: "当前选中代码行。"
        }
      ],
      edges: []
    },
    slice: {
      lines: [line],
      code: codeLine
    },
    insight: {
      target: `第 ${line} 行`,
      checkerConclusion: checkerConclusion || checkerVerdict || "UNKNOWN",
      proofOutcome: checkerVerdict || checkerConclusion || checkerStatus || "NOT_PROVED",
      failureReason: checkerMessage || "无",
      highlightExplanation: "橙色节点表示当前定位焦点。"
    }
  };
};

const requestVerificationSummary = async ({
  candidateFunction = "",
  checkerStatus = "",
  checkerVerdict = "",
  checkerConclusion = "",
  checkerMessage = "",
  checkerCounterexample = "",
  selectedLine = 1
} = {}) => {
  const normalizedSelectedLine = Math.max(1, Number(selectedLine || 1));
  try {
    const summary = await buildVerificationSummaryGraph({
      code: String(form.code || ""),
      language: String(form.language || ""),
      candidateFunction: String(candidateFunction || ""),
      checkerStatus: String(checkerStatus || ""),
      checkerVerdict: String(checkerVerdict || ""),
      checkerConclusion: String(checkerConclusion || ""),
      checkerMessage: String(checkerMessage || ""),
      checkerCounterexample: String(checkerCounterexample || ""),
      selectedLine: normalizedSelectedLine
    });
    result.verificationSummary = summary || buildVerificationSummaryFallback({
      candidateFunction,
      checkerStatus,
      checkerVerdict,
      checkerConclusion,
      checkerMessage,
      selectedLine: normalizedSelectedLine
    });
  } catch (error) {
    appendLog(`可视化摘要生成失败，已回退占位图: ${error?.message || "未知错误"}`);
    result.verificationSummary = buildVerificationSummaryFallback({
      candidateFunction,
      checkerStatus,
      checkerVerdict,
      checkerConclusion,
      checkerMessage,
      selectedLine: normalizedSelectedLine
    });
  }
};

const handleModeChange = (mode) => {
  currentMode.value = mode === "verification" ? "verification" : "learning";
  if (currentMode.value === "learning") {
    form.batchMode = false;
    hydrateLearningFromCode(form.code, { keepLineSelection: true });
    if (!learning.lineExplanation) {
      handleCodeLineSelect({ lineNumber: 1, lineText: String(form.code || "").split("\n")[0] || "" });
    }
    return;
  }
  verification.selectedLine = Math.max(1, Number(verification.selectedLine || 1));
  verification.lineText = resolveLineTextByNumber(form.code, verification.selectedLine);
  if (!result.verificationSummary && String(form.code || "").trim()) {
    result.verificationSummary = buildVerificationSummaryFallback({
      candidateFunction: result.candidateFunctions,
      checkerStatus: result.checkerStatus,
      checkerVerdict: result.checkerVerdict,
      checkerConclusion: result.checkerConclusion,
      checkerMessage: result.checkerMessage,
      selectedLine: verification.selectedLine
    });
  }
};

const resolveBatchDataset = () => {
  const candidate = String(caseSource.dataset || form.benchmark || "").trim();
  if (!candidate || candidate.toLowerCase() === "none") {
    return "";
  }
  return candidate;
};

const buildBatchCaseCacheKey = (dataset, entryIndex) => `${dataset}::${entryIndex}`;

const resetBatchRuntimeBinding = () => {
  batchCaseSyncToken += 1;
  batchCaseSyncInFlightKey = "";
  batchCodeBoundKey = "";
  batchCaseSourceCache.clear();
};

const applyCaseSourceToEditor = (response, dataset, entryIndex) => {
  if (!response?.code || !String(response.code).trim()) {
    return false;
  }
  form.code = String(response.code);
  selectedFileName.value = "";
  caseSource.dataset = dataset;
  caseSource.csvPath = response.csvPath || caseSource.csvPath;
  caseSource.programPath = response.programPath || caseSource.programPath;
  caseSource.programClass = response.programClass || caseSource.programClass;
  caseSource.programFunction = response.programFunction || caseSource.programFunction;
  caseSource.entryIndex = Number(response.entryIndex ?? entryIndex ?? 0);
  caseSource.totalEntries = Number(response.totalEntries ?? caseSource.totalEntries ?? 0);
  caseSource.loaded = true;
  return true;
};

const syncCurrentBatchCaseSource = async (dataset, entryIndex, currentCaseName) => {
  const normalizedDataset = String(dataset || "").trim();
  if (!normalizedDataset) {
    return;
  }
  const normalizedIndex = Number(entryIndex);
  if (!Number.isFinite(normalizedIndex) || normalizedIndex < 0) {
    return;
  }
  const cacheKey = buildBatchCaseCacheKey(normalizedDataset, normalizedIndex);
  if (cacheKey === batchCodeBoundKey) {
    return;
  }
  const token = ++batchCaseSyncToken;
  const cached = batchCaseSourceCache.get(cacheKey);
  if (cached) {
    batchCaseSyncInFlightKey = "";
    if (applyCaseSourceToEditor(cached, normalizedDataset, normalizedIndex)) {
      batchCodeBoundKey = cacheKey;
    }
    return;
  }
  if (batchCaseSyncInFlightKey === cacheKey) {
    return;
  }

  batchCaseSyncInFlightKey = cacheKey;
  try {
    const response = await loadCaseSource(normalizedDataset, normalizedIndex);
    if (token !== batchCaseSyncToken) {
      return;
    }
    const status = String(response?.status || "").toUpperCase();
    if (status !== "SUCCESS") {
      throw new Error(response?.message || "案例源码加载失败");
    }
    batchCaseSourceCache.set(cacheKey, response);
    if (applyCaseSourceToEditor(response, normalizedDataset, normalizedIndex)) {
      batchCodeBoundKey = cacheKey;
      appendLog(`已切换当前运行案例源码: ${currentCaseName || `entry-${normalizedIndex + 1}`}`);
    }
  } catch (error) {
    if (token !== batchCaseSyncToken) {
      return;
    }
    appendLog(`当前案例源码刷新失败: ${error.message || "未知错误"}`);
  } finally {
    if (token === batchCaseSyncToken && batchCaseSyncInFlightKey === cacheKey) {
      batchCaseSyncInFlightKey = "";
    }
  }
};

const clearOutput = () => {
  stopBatchPolling();
  resetBatchRuntimeBinding();
  singleRunAbortController = null;
  selectedBatchCaseKey.value = "";
  logs.value = "";
  result.candidateFunctions = "";
  result.checkerStatus = "";
  result.checkerVerdict = "";
  result.checkerConclusion = "";
  result.checkerMessage = "";
  result.attemptCount = 0;
  result.maxAttempts = 0;
  result.checkerCounterexample = "";
  result.checkerRawOutput = "";
  result.checkerFeedback = "";
  result.artifactSummary = "";
  result.verificationSummary = null;
  result.batchMode = false;
  result.batchProgress.total = 0;
  result.batchProgress.completed = 0;
  result.batchProgress.currentIndex = 0;
  result.batchProgress.currentCase = "";
  result.batchProgress.currentAttempt = 0;
  result.batchProgress.currentAttemptStates = ["", "", "", "", ""];
  result.batchProgress.provedCount = 0;
  result.batchProgress.notProvedCount = 0;
  result.batchProgress.stopCount = 0;
  result.batchResults = [];
  result.batchResultPath = "";
  verification.selectedLine = 1;
  verification.lineText = "";
};

const normalizeCheckerStatus = (value) => {
  const status = String(value || "").toUpperCase();
  if (status === "PASSED" || status === "PROVED" || status === "FAILED" || status === "DISPROVED" || status === "NOT_PROVED") return "COMPLETED";
  if (status === "ERROR") return "CHECKER_ERROR";
  return status;
};

const normalizeCheckerConclusion = (value, checkerStatusRaw) => {
  const conclusion = String(value || "").toUpperCase();
  if (conclusion === "YES" || conclusion === "NO") return conclusion;
  const legacy = String(checkerStatusRaw || "").toUpperCase();
  if (legacy === "PASSED" || legacy === "PROVED") return "YES";
  if (legacy === "FAILED" || legacy === "DISPROVED" || legacy === "NOT_PROVED") return "NO";
  return "";
};

const normalizeCheckerVerdict = (value, conclusion) => {
  const verdict = String(value || "").toUpperCase();
  if (verdict === "PROVED") return "PROVED";
  if (verdict === "DISPROVED" || verdict === "NOT_PROVED") return "NOT_PROVED";
  if (conclusion === "YES") return "PROVED";
  if (conclusion === "NO") return "NOT_PROVED";
  return "";
};

const normalizeExecutionStatus = (value) => {
  const status = String(value || "").toUpperCase();
  if (status === "COMPLETED" || status === "ERROR" || status === "SKIPPED") {
    return status;
  }
  if (status === "CHECKER_ERROR") {
    return "ERROR";
  }
  return status || "ERROR";
};

const sanitizeCheckerUserMessage = (value) => {
  const source = String(value || "");
  if (!source) {
    return "";
  }
  return source.replace(/^\s*(?:\[checker-runtime-v\d+\]\s*)+/i, "").trim();
};

const isAffineTemplatePrecheckFailure = (value) => {
  const source = String(value || "");
  if (!source) {
    return false;
  }
  return /AFFINE_TEMPLATE_PRECHECK_FAILED|affine\s*template|linear\s*template|non-affine|variable\*variable|unsupported operator/i.test(source);
};

const summarizeCheckerFacingMessage = (value) => {
  const normalized = sanitizeCheckerUserMessage(value);
  if (!normalized) {
    return "";
  }
  if (isAffineTemplatePrecheckFailure(normalized)) {
    return "候选函数不符合当前验证器支持的线性模板，系统已自动重试。";
  }
  return normalized.length > 180 ? `${normalized.slice(0, 180)}...` : normalized;
};

const normalizeVerificationStatus = (value, conclusion, executionStatus) => {
  const status = String(value || "").toUpperCase();
  if (status === "PROVED") return "PROVED";
  if (status === "STOP") return "STOP";
  if (status === "NOT_PROVED" || status === "DISPROVED") return "NOT_PROVED";
  if (conclusion === "NO_RELU_SOLUTION") return "STOP";
  if (conclusion === "YES") return "PROVED";
  if (conclusion === "NO") return "NOT_PROVED";
  if (executionStatus === "ERROR") return "NOT_PROVED";
  return "NOT_PROVED";
};

const normalizeBatchCaseRow = (row) => {
  const caseName = row.caseName || row.case_name || "";
  const candidateFunction = row.candidateFunction || row.candidate_function || "";
  const executionStatus = normalizeExecutionStatus(row.executionStatus || row.execution_status || row.checkerStatus || row.checker_status);
  const finalStatusRaw = row.finalStatus || row.final_status || "";
  const finalStatus = String(finalStatusRaw || "").toUpperCase();
  const conclusionRaw = row.conclusion || row.checkerConclusion || row.checker_conclusion || "";
  let conclusion = String(conclusionRaw || "").toUpperCase();
  if (finalStatus === "STOP") {
    conclusion = "NO_RELU_SOLUTION";
  } else if (conclusion !== "YES" && conclusion !== "NO") {
    conclusion = executionStatus === "ERROR" ? "NO" : "NO";
  }
  const verificationStatus = normalizeVerificationStatus(
    row.verificationStatus || row.verification_status || row.checkerVerdict || row.checker_verdict,
    conclusion,
    executionStatus
  );
  const attemptStatesSource = row.attemptStates || row.attempt_states || [];
  const attemptStates = Array.isArray(attemptStatesSource)
    ? attemptStatesSource.map((item) => String(item || "").toUpperCase()).slice(0, 5)
    : [];
  while (attemptStates.length < 5) {
    attemptStates.push("");
  }
  return {
    caseName,
    candidateFunction,
    executionStatus,
    verificationStatus,
    conclusion,
    attemptCount: Number(row.attemptCount ?? row.attempt_count ?? 0),
    finalStatus: finalStatus || (verificationStatus === "PROVED" ? "PROVED" : verificationStatus === "STOP" ? "STOP" : "NOT_PROVED"),
    stopReason: row.stopReason || row.stop_reason || "",
    attemptStates,
    counterexample: row.counterexample || row.checkerCounterexample || row.checker_counterexample || "",
    message: sanitizeCheckerUserMessage(row.message || row.checkerMessage || row.checker_message || ""),
    debugMessage: row.debugMessage || row.debug_message || "",
    traceTag: row.traceTag || row.trace_tag || "",
    checkerRawOutput: row.checkerRawOutput || row.checker_raw_output || "",
    checkerFeedback: row.checkerFeedback || row.checker_feedback || "",
    log: row.log || ""
  };
};

const schedulePersist = () => {
  if (persistDebounceTimer) {
    clearTimeout(persistDebounceTimer);
  }
  persistDebounceTimer = setTimeout(() => {
    persistDebounceTimer = null;
    runtimeStore.persist();
  }, 180);
};

const flushPersist = () => {
  if (persistDebounceTimer) {
    clearTimeout(persistDebounceTimer);
    persistDebounceTimer = null;
  }
  runtimeStore.persist();
};

const updateScrollSnapshot = () => {
  runtimeState.ui.scrollTopWindow = Math.max(0, Number(window.scrollY || window.pageYOffset || 0));
  runtimeState.ui.scrollTopOperation = Math.max(0, Number(operationRef.value?.scrollTop || 0));
  runtimeState.ui.scrollTopDisplay = Math.max(0, Number(displayRef.value?.scrollTop || 0));
};

const restoreScrollSnapshot = () => {
  if (operationRef.value) {
    operationRef.value.scrollTop = Math.max(0, Number(runtimeState.ui.scrollTopOperation || 0));
  }
  if (displayRef.value) {
    displayRef.value.scrollTop = Math.max(0, Number(runtimeState.ui.scrollTopDisplay || 0));
  }
  const windowTop = Math.max(0, Number(runtimeState.ui.scrollTopWindow || 0));
  if (windowTop > 0) {
    window.scrollTo({ top: windowTop, behavior: "auto" });
  }
};

const handleSelectedCaseKeyUpdate = (nextKey) => {
  selectedBatchCaseKey.value = String(nextKey || "");
};

const stopBatchPolling = ({ clearTaskContext = true } = {}) => {
  if (batchPollingTimer) {
    clearInterval(batchPollingTimer);
    batchPollingTimer = null;
  }
  batchPollingInFlight = false;
  if (clearTaskContext) {
    activeBatchTaskId.value = "";
    lastBatchCompletedCases.value = 0;
  }
};

const pauseTask = async () => {
  if (!submitting.value) {
    return;
  }

  if (activeBatchTaskId.value) {
    const taskId = activeBatchTaskId.value;
    appendLog(`收到暂停请求: taskId=${taskId}`);
    try {
      const pauseResp = await pauseBatchTask(taskId);
      if (!pauseResp?.success) {
        throw new Error(pauseResp?.message || "暂停请求失败");
      }
      const status = String(pauseResp?.status || "").toUpperCase();
      if (status === "PAUSED") {
        appendLog("批量任务已暂停。");
        result.checkerStatus = "PAUSED";
        result.checkerMessage = pauseResp?.message || "批量验证已暂停";
        result.checkerFeedback = result.checkerMessage;
        ElMessage.warning("批量验证已暂停");
        submitting.value = false;
        stopBatchPolling({ clearTaskContext: false });
        return;
      }
      appendLog("批量任务暂停请求已发送：当前案例完成后将暂停后续案例。");
      result.checkerStatus = "PAUSING";
      result.checkerMessage = "暂停请求已提交：当前案例完成后将暂停后续案例。";
      result.checkerFeedback = result.checkerMessage;
      ElMessage.warning("已发送暂停请求：当前案例完成后暂停后续案例");
    } catch (error) {
      const readable = error.message || "暂停请求失败";
      appendLog(`批量任务暂停失败: ${readable}`);
      ElMessage.error(readable);
    }
    return;
  }

  if (singleRunAbortController) {
    appendLog("收到暂停请求: 正在中止当前单案例验证请求。");
    singleRunAbortController.abort();
  }
};

const shouldBlockRouteLeave = () => {
  const checkerStatus = String(result.checkerStatus || "").toUpperCase();
  if (!activeBatchTaskId.value) {
    return false;
  }
  return submitting.value || isRunningBatchStatus(checkerStatus);
};

const handleBeforeUnload = (event) => {
  if (!shouldBlockRouteLeave()) {
    return;
  }
  event.preventDefault();
  event.returnValue = leavePromptMessage;
  return leavePromptMessage;
};

const clearPendingRouteLeave = () => {
  pendingRouteLeaveNext = null;
};

const handleLeaveDialogCancel = () => {
  leaveDialogVisible.value = false;
  if (pendingRouteLeaveNext) {
    pendingRouteLeaveNext(false);
  }
  clearPendingRouteLeave();
};

const handleLeaveDialogConfirm = () => {
  leaveDialogVisible.value = false;
  updateScrollSnapshot();
  flushPersist();
  const next = pendingRouteLeaveNext;
  clearPendingRouteLeave();
  bypassRouteLeaveGuardOnce = true;
  if (next) {
    next();
  }
};

const applyBatchStatusSnapshot = (snapshot) => {
  const rowsRaw = snapshot.results || snapshot.batch_results || [];
  const rows = Array.isArray(rowsRaw) ? rowsRaw.map(normalizeBatchCaseRow) : [];
  const totalCases = Math.max(0, Number(snapshot.totalCount ?? snapshot.totalCases ?? snapshot.total_cases ?? snapshot.batch_total ?? 0));
  const completedCases = Math.max(0, Number(snapshot.completedCount ?? snapshot.completedCases ?? snapshot.completed_cases ?? snapshot.batch_completed ?? rows.length));
  const provedCount = Number(snapshot.provedCount ?? snapshot.proved_count ?? snapshot.batch_proved ?? rows.filter((row) => row.verificationStatus === "PROVED").length);
  const notProvedCount = Number(snapshot.notProvedCount ?? snapshot.not_proved_count ?? snapshot.batch_not_proved
    ?? rows.filter((row) => row.verificationStatus === "NOT_PROVED").length);
  const stopCount = Number(snapshot.stopCount ?? snapshot.stop_count ?? snapshot.batch_stop
    ?? rows.filter((row) => {
      const stopReason = String(row.stopReason || "").trim();
      if (stopReason) {
        return true;
      }
      const finalStatus = String(row.finalStatus || "").toUpperCase();
      if (finalStatus === "STOP") {
        return true;
      }
      const states = Array.isArray(row.attemptStates) ? row.attemptStates : [];
      return states.some((state) => String(state || "").toUpperCase() === "STOP");
    }).length);
  const currentCaseName = snapshot.currentCaseName || snapshot.current_case_name || snapshot.batch_current_case || "";
  const currentIndexRaw = Number(snapshot.currentIndex ?? snapshot.current_index ?? completedCases);
  const currentIndex = Number.isFinite(currentIndexRaw)
    ? Math.max(0, totalCases > 0 ? Math.min(totalCases - 1, currentIndexRaw) : currentIndexRaw)
    : Math.max(0, completedCases);
  const currentAttempt = Number(snapshot.currentAttempt ?? snapshot.current_attempt ?? 0);
  const currentAttemptStatesRaw = snapshot.currentAttemptStates ?? snapshot.current_attempt_states ?? [];
  const currentAttemptStates = Array.isArray(currentAttemptStatesRaw)
    ? currentAttemptStatesRaw.map((item) => String(item || "").toUpperCase()).slice(0, 5)
    : [];
  while (currentAttemptStates.length < 5) {
    currentAttemptStates.push("");
  }
  const status = String(snapshot.status || "").toUpperCase();
  const messageRaw = sanitizeCheckerUserMessage(snapshot.message || "");
  const message = summarizeCheckerFacingMessage(messageRaw);
  const resultPath = snapshot.resultPath || snapshot.result_path || snapshot.batch_result_path || "";

  result.batchMode = true;
  result.batchResults = rows;
  result.batchProgress.total = totalCases;
  result.batchProgress.completed = completedCases;
  result.batchProgress.currentIndex = currentIndex;
  result.batchProgress.currentCase = currentCaseName;
  result.batchProgress.currentAttempt = currentAttempt;
  result.batchProgress.currentAttemptStates = currentAttemptStates;
  result.batchProgress.provedCount = provedCount;
  result.batchProgress.notProvedCount = notProvedCount;
  result.batchProgress.stopCount = stopCount;
  result.batchResultPath = resultPath;
  result.candidateFunctions = "";
  result.attemptCount = 0;
  result.maxAttempts = 0;
  result.checkerStatus = status || "RUNNING";
  result.checkerVerdict = "";
  result.checkerConclusion = "";
  result.checkerMessage = message || "";
  result.checkerCounterexample = "";
  result.checkerRawOutput = "";
  result.checkerFeedback = messageRaw || message || "";
  result.artifactSummary = resultPath ? `批量结果文件: ${resultPath}` : "批量处理中...";

  if (completedCases > lastBatchCompletedCases.value) {
    appendLog(`批量进度更新: ${completedCases}/${totalCases}, 当前案例=${currentCaseName || "-"}`);
    lastBatchCompletedCases.value = completedCases;
  }

  if (status === "RUNNING" || status === "PAUSING") {
    const dataset = resolveBatchDataset();
    if (dataset) {
      void syncCurrentBatchCaseSource(dataset, currentIndex, currentCaseName);
    }
  }

  if (status === "COMPLETED") {
    appendLog(`批量处理完成: total=${totalCases}, completed=${completedCases}, PROVED=${provedCount}, NOT_PROVED=${notProvedCount}, STOP=${stopCount}`);
    ElMessage.success("批量验证完成");
    submitting.value = false;
    stopBatchPolling({ clearTaskContext: true });
  } else if (status === "PAUSED") {
    appendLog("批量处理已暂停");
    ElMessage.warning(message || "批量验证已暂停");
    submitting.value = false;
    stopBatchPolling({ clearTaskContext: false });
  } else if (status === "PAUSING") {
    result.checkerMessage = message || "暂停请求已提交：当前案例完成后将暂停后续案例。";
    result.checkerFeedback = result.checkerMessage;
  } else if (status === "FAILED") {
    appendLog(`批量处理失败: ${messageRaw || "未知错误"}`);
    ElMessage.error(message || "批量处理失败，请查看运行日志。");
    submitting.value = false;
    stopBatchPolling({ clearTaskContext: true });
  }
};

const pollBatchStatusOnce = async () => {
  if (!activeBatchTaskId.value || batchPollingInFlight) {
    return;
  }
  batchPollingInFlight = true;
  try {
    const snapshot = await fetchBatchStatus(activeBatchTaskId.value);
    applyBatchStatusSnapshot(snapshot || {});
  } catch (error) {
    const message = error.message || "未知错误";
    appendLog(`批量进度查询失败: ${message}`);
    const lower = String(message).toLowerCase();
    if (lower.includes("404") || lower.includes("not found")) {
      submitting.value = false;
      stopBatchPolling({ clearTaskContext: true });
      ElMessage.error("批量任务不存在或已失效，请重新启动。");
    }
  } finally {
    batchPollingInFlight = false;
  }
};

const startBatchPolling = (taskId) => {
  stopBatchPolling({ clearTaskContext: false });
  activeBatchTaskId.value = String(taskId || "");
  if (!activeBatchTaskId.value) {
    return;
  }
  batchPollingTimer = setInterval(() => {
    void pollBatchStatusOnce();
  }, 1000);
  void pollBatchStatusOnce();
};

const isRunningBatchStatus = (status) => {
  const normalized = String(status || "").toUpperCase();
  return normalized === "RUNNING" || normalized === "PAUSING";
};

const recoverBatchTaskState = async () => {
  const taskId = String(activeBatchTaskId.value || "").trim();
  if (!taskId) {
    return;
  }
  appendLog(`检测到历史批量任务: taskId=${taskId}，开始恢复任务状态...`);
  try {
    const snapshot = await fetchBatchStatus(taskId);
    applyBatchStatusSnapshot(snapshot || {});
    const status = String(snapshot?.status || "").toUpperCase();
    if (isRunningBatchStatus(status)) {
      submitting.value = true;
      startBatchPolling(taskId);
      appendLog("批量任务恢复成功，已重新接入实时轮询。");
      return;
    }
    if (status === "PAUSED") {
      submitting.value = false;
      stopBatchPolling({ clearTaskContext: false });
      appendLog("批量任务当前处于暂停状态，已恢复结果展示。");
      return;
    }
    if (status === "COMPLETED" || status === "FAILED") {
      submitting.value = false;
      stopBatchPolling({ clearTaskContext: true });
      appendLog(`批量任务已结束，状态=${status}。`);
    }
  } catch (error) {
    const readable = error?.message || "未知错误";
    appendLog(`批量任务恢复失败: ${readable}`);
    const lower = String(readable).toLowerCase();
    if (lower.includes("404") || lower.includes("not found")) {
      stopBatchPolling({ clearTaskContext: true });
      submitting.value = false;
      ElMessage.warning("历史批量任务不存在或已过期，已清理恢复上下文。");
    }
  }
};

const resetCaseSourceMeta = () => {
  caseSource.dataset = "";
  caseSource.csvPath = "";
  caseSource.programPath = "";
  caseSource.programClass = "";
  caseSource.programFunction = "";
  caseSource.entryIndex = 0;
  caseSource.totalEntries = 0;
  caseSource.loaded = false;
  caseSource.loading = false;
  resetBatchRuntimeBinding();
};

const resetForm = () => {
  caseLoadToken += 1;
  form.code = "";
  form.benchmark = "none";
  form.model = "kimi-k2.5";
  form.language = "python";
  form.batchMode = false;
  selectedFileName.value = "";
  resetCaseSourceMeta();
  clearOutput();
  resetLearningPanels();
};

const handleFileChange = (file) => {
  if (!file?.raw) return;
  selectedFileName.value = file.raw.name || "";
  const reader = new FileReader();
  reader.onload = () => {
    form.code = String(reader.result || "");
    if (isLearningMode.value) {
      hydrateLearningFromCode(form.code, { keepLineSelection: false });
    }
  };
  reader.readAsText(file.raw);
};

const loadCaseProgramSource = async (dataset) => {
  if (!dataset || dataset === "none") {
    return;
  }

  const token = ++caseLoadToken;
  caseSource.loading = true;
  caseSource.loaded = false;
  caseSource.dataset = dataset;

  appendLog(`已选择数据集: ${dataset}`);
  appendLog("开始读取案例 CSV");

  try {
    const response = await loadCaseSource(dataset, 0);
    if (token !== caseLoadToken || form.benchmark !== dataset) {
      return;
    }
    if (response.log) {
      logs.value = logs.value ? `${logs.value}\n${response.log}` : response.log;
    }

    const status = String(response.status || "").toUpperCase();
    if (status !== "SUCCESS") {
      throw new Error(response.message || "案例源码加载失败");
    }
    if (!response.code || !response.code.trim()) {
      throw new Error("案例源码为空，无法用于分析");
    }

    form.code = response.code;
    selectedFileName.value = "";
    caseSource.csvPath = response.csvPath || "";
    caseSource.programPath = response.programPath || "";
    caseSource.programClass = response.programClass || "";
    caseSource.programFunction = response.programFunction || "";
    caseSource.entryIndex = Number(response.entryIndex || 0);
    caseSource.totalEntries = Number(response.totalEntries || 0);
    caseSource.loaded = true;
    const cacheKey = buildBatchCaseCacheKey(dataset, caseSource.entryIndex);
    batchCaseSourceCache.set(cacheKey, response);
    batchCodeBoundKey = cacheKey;

    appendLog(`已读取 CSV: ${caseSource.csvPath || "未知"}`);
    appendLog(
      `已定位程序: ${caseSource.programClass || "未知"}${caseSource.programFunction ? `#${caseSource.programFunction}` : ""}`
    );
    appendLog(`已加载源码: ${caseSource.programPath || "未知"}`);
    appendLog("已将源码回填到代码输入框");
  } catch (error) {
    if (token !== caseLoadToken) {
      return;
    }
    const readable = error.message || "案例源码加载失败";
    caseSource.loaded = false;
    appendLog(`案例加载失败: ${readable}`);
    ElMessage.error(readable);
  } finally {
    if (token === caseLoadToken) {
      caseSource.loading = false;
    }
  }
};

watch(
  () => form.benchmark,
  (value, previous) => {
    if (value === previous) {
      return;
    }

    if (!value || value === "none") {
      caseLoadToken += 1;
      caseSource.loading = false;
      caseSource.loaded = false;
      caseSource.dataset = "";
      caseSource.csvPath = "";
      caseSource.programPath = "";
      caseSource.programClass = "";
      caseSource.programFunction = "";
      caseSource.entryIndex = 0;
      caseSource.totalEntries = 0;
      resetBatchRuntimeBinding();
      if (previous && previous !== "none") {
        appendLog("案例选择已切回 None，语言与代码可手动调整。");
      }
      return;
    }

    void loadCaseProgramSource(value);
  }
);

watch(
  () => form.code,
  (code) => {
    if (!isLearningMode.value) {
      const lines = String(code || "").split("\n");
      const maxLine = Math.max(1, lines.length);
      verification.selectedLine = Math.max(1, Math.min(maxLine, Number(verification.selectedLine || 1)));
      verification.lineText = String(lines[verification.selectedLine - 1] || "");
      result.verificationSummary = null;
      return;
    }
    if (!String(code || "").trim()) {
      resetLearningPanels();
      return;
    }
    hydrateLearningFromCode(code, { keepLineSelection: true });
  }
);

watch(
  () => runtimeState,
  () => {
    schedulePersist();
  },
  { deep: true }
);

const handleWindowScroll = () => {
  updateScrollSnapshot();
  schedulePersist();
};

const handleOperationScroll = () => {
  updateScrollSnapshot();
  schedulePersist();
};

const handleDisplayScroll = () => {
  updateScrollSnapshot();
  schedulePersist();
};

const handleLearningRunCode = async () => {
  if (!isLearningMode.value || learningCodeRunState.value === "running") {
    return;
  }
  if (!String(form.code || "").trim()) {
    ElMessage.warning("请先输入代码");
    return;
  }
  if (!isLearningCodeRunnable.value) {
    ElMessage.info("当前语言暂不支持在此面板一键运行，请使用本地环境执行。");
    return;
  }

  learningCodeRunState.value = "running";
  learning.programOutput = "";
  const minVisibleMs = 520;
  const started = Date.now();

  const finishRunning = () => {
    const elapsed = Date.now() - started;
    if (elapsed < minVisibleMs) {
      window.setTimeout(() => {
        learningCodeRunState.value = "idle";
      }, minVisibleMs - elapsed);
    } else {
      learningCodeRunState.value = "idle";
    }
  };

  try {
    const data = await runLearningCodeSnippet({
      code: String(form.code || ""),
      language: String(form.language || "")
    });
    const stdout = data && data.stdout != null ? String(data.stdout) : "";
    const stderr = data && data.stderr != null ? String(data.stderr) : "";
    const merged = [stdout, stderr].map((s) => s.trim()).filter(Boolean).join("\n").trim();
    const fallbackMsg = data && data.message != null ? String(data.message).trim() : "";
    learning.programOutput = merged || fallbackMsg || "（无输出）";
    appendLog("运行代码：后端已返回执行结果。");
    ElMessage.success("运行完成");
  } catch (error) {
    const msg = String(error?.message || "运行失败");
    const serviceMissing =
      /\b404\b|405|Not Found|接口不可用|run-code|Failed to fetch|NetworkError|LOAD_FAILED/i.test(msg);
    await new Promise((resolve) => {
      window.setTimeout(resolve, minVisibleMs);
    });
    if (serviceMissing) {
      learning.programOutput =
        "（演示）后端执行接口未就绪，暂无真实输出。接入运行服务后，结果将显示在此。";
      appendLog("运行代码：后端执行接口不可用，已展示占位说明。");
      ElMessage.info("运行服务暂未接入，可在本地环境执行代码。");
    } else {
      learning.programOutput = `运行失败：${msg}`;
      appendLog(`运行代码失败：${msg}`);
      ElMessage.warning("运行失败，请查看输出区说明");
    }
  } finally {
    finishRunning();
  }
};

const submitTask = async () => {
  if (isLearningMode.value) {
    if (!String(form.code || "").trim()) {
      ElMessage.warning("请先输入代码，再开始讲解");
      return;
    }
    submitting.value = true;
    appendLog("开始讲解：正在解析代码结构与执行顺序。");
    try {
      hydrateLearningFromCode(form.code, { keepLineSelection: true });
      const targetLine = Math.max(1, Number(learning.selectedLine || 1));
      const targetLineText = resolveLineTextByNumber(form.code, targetLine);

      appendLog("正在调用后端讲解服务：生成逐行解释与代码分块。");
      const response = await explainLearningCode({
        code: String(form.code || ""),
        model: String(form.model || ""),
        language: String(form.language || ""),
        fileName: String(selectedFileName.value || ""),
        selectedLine: targetLine
      });

      if (response?.log) {
        logs.value = logs.value ? `${logs.value}\n${response.log}` : response.log;
      }

      applyLearningExplainResponse(response, targetLine, targetLineText);
      const status = String(response?.status || "").toUpperCase();
      if (status === "SUCCESS") {
        appendLog("讲解生成完成：当前代码行讲解与当前代码块讲解已更新。");
        ElMessage.success("讲解内容已更新");
      } else if (status === "FALLBACK") {
        appendLog("模型讲解未成功，已切换为后端兜底讲解结果。");
        ElMessage.warning(response?.message || "已回退到兜底讲解结果");
      } else {
        appendLog("讲解请求已完成，但返回状态未标记为 SUCCESS。");
        ElMessage.info(response?.message || "讲解结果已返回");
      }
    } catch (error) {
      const readable = error?.message || "讲解请求失败";
      appendLog(`讲解生成失败：${readable}`);
      const fallbackLine = Math.max(1, Number(learning.selectedLine || 1));
      applyLearningSelection(fallbackLine, resolveLineTextByNumber(form.code, fallbackLine));
      ElMessage.error(readable);
    } finally {
      submitting.value = false;
    }
    return;
  }

  const hasCodeInput = Boolean(form.code.trim());
  const hasUploadedFile = Boolean(selectedFileName.value.trim());
  const hasSelectedCase = Boolean(form.benchmark && form.benchmark !== "none");
  const isBatchMode = Boolean(form.batchMode);
  const checkerStatus = String(result.checkerStatus || "").toUpperCase();

  if (activeBatchTaskId.value && (submitting.value || isRunningBatchStatus(checkerStatus))) {
    ElMessage.warning(`当前批量任务仍在运行中（taskId=${activeBatchTaskId.value}），请勿重复创建任务`);
    return;
  }

  if (caseSource.loading) {
    ElMessage.warning("案例源码正在加载，请稍候再启动验证");
    return;
  }

  if (isBatchMode && !hasSelectedCase) {
    ElMessage.warning("批量处理需要先选择数据集");
    return;
  }

  if (isBatchMode && hasSelectedCase && !caseSource.loaded) {
    ElMessage.warning("批量处理前需要先成功加载案例元数据");
    return;
  }

  if (!hasCodeInput && !hasUploadedFile && !hasSelectedCase) {
    ElMessage.warning("请输入代码、上传文件或选择案例");
    return;
  }
  if (!hasCodeInput) {
    if (hasSelectedCase) {
      if (!caseSource.loaded) {
        ElMessage.warning("案例源码尚未加载成功，请重新选择案例或手动输入代码");
      } else {
        ElMessage.warning("当前代码为空，请确认案例源码已加载或手动输入代码");
      }
      return;
    }
    if (hasUploadedFile) {
      ElMessage.warning("上传文件内容尚未读取成功，请重新上传或稍后重试");
      return;
    }
    ElMessage.warning("请输入代码、上传文件或选择案例");
    return;
  }

  clearOutput();
  submitting.value = true;
  appendLog("开始分析");
  let keepSubmittingForBatch = false;

  try {
    appendLog("准备请求参数");
    if (hasSelectedCase) {
      appendLog(`已选择数据集: ${form.benchmark}`);
      if (caseSource.csvPath) {
        appendLog(`已读取 CSV: ${caseSource.csvPath}`);
      }
      if (caseSource.programPath) {
        appendLog(`已定位程序源码: ${caseSource.programPath}`);
      }
    }
    appendLog("已将源码送入模型分析");
    if (isBatchMode) {
      appendLog(`批量模式已启用，预计处理 ${caseSource.totalEntries || 0} 个案例`);
      result.batchMode = true;
      result.attemptCount = 0;
      result.maxAttempts = 0;
      result.batchProgress.total = caseSource.totalEntries || 0;
      result.batchProgress.completed = 0;
      result.batchProgress.currentIndex = caseSource.entryIndex || 0;
      result.batchProgress.currentCase = "处理中...";
      result.batchProgress.currentAttempt = 0;
      result.batchProgress.currentAttemptStates = ["", "", "", "", ""];
      result.batchProgress.provedCount = 0;
      result.batchProgress.notProvedCount = 0;
      result.batchProgress.stopCount = 0;
      result.batchResults = [];
      result.batchResultPath = "";
    }

    const payload = {
      code: form.code,
      model: form.model,
      language: form.language,
      case: form.benchmark === "none" ? "" : form.benchmark,
      fileName: selectedFileName.value,
      extraConfig: "",
      batchMode: isBatchMode,
      batch_mode: isBatchMode
    };

    if (isBatchMode) {
      singleRunAbortController = null;
      appendLog("提交批量任务创建请求");
      const startResponse = await startBatchTask(payload);
      const taskId = startResponse.taskId || startResponse.task_id || "";
      if (!taskId) {
        throw new Error(startResponse.message || "批量任务创建失败，未返回 taskId");
      }
      keepSubmittingForBatch = true;
      activeBatchTaskId.value = taskId;
      appendLog(`批量任务已创建: taskId=${taskId}`);
      result.checkerStatus = "RUNNING";
      result.checkerMessage = "Batch task is running.";
      result.checkerFeedback = "Batch task is running.";
      startBatchPolling(taskId);
      ElMessage.success("批量任务已启动");
      return;
    }

    singleRunAbortController = new AbortController();
    appendLog("提交后端请求");
    const response = await generateRankingFunction(payload, { signal: singleRunAbortController.signal });
    singleRunAbortController = null;

    if (response.log) {
      logs.value = logs.value ? `${logs.value}\n${response.log}` : response.log;
    }

    const status = response.status || "FAILED";
    const message = response.message || "";
    const candidateFunction = response.candidate_function || response.candidateFunction || "";
    const rawResponse = response.raw_response || response.rawResponse || "";
    const checkerStatusRaw = response.checker_status || response.checkerStatus || "";
    const checkerStatus = normalizeCheckerStatus(checkerStatusRaw);
    const checkerConclusionRaw = response.checker_conclusion || response.checkerConclusion || "";
    const checkerConclusion = normalizeCheckerConclusion(checkerConclusionRaw, checkerStatusRaw);
    const checkerVerdictRaw = response.checker_verdict || response.checkerVerdict || "";
    const checkerVerdict = normalizeCheckerVerdict(checkerVerdictRaw, checkerConclusion);
    const checkerMessageRaw = sanitizeCheckerUserMessage(response.checker_message || response.checkerMessage || "");
    const checkerMessage = summarizeCheckerFacingMessage(checkerMessageRaw);
    const checkerRawOutput = response.checker_raw_output || response.checkerRawOutput || "";
    const checkerCounterexample = response.checker_counterexample || response.checkerCounterexample || "";
    const checkerFeedback = response.checker_feedback || response.checkerFeedback || "";
    const finalSummary = response.final_summary || response.finalSummary || "";
    const attemptCount = Math.max(0, Number(response.attempt_count ?? response.attemptCount ?? 0));
    const maxAttempts = Math.max(0, Number(response.max_attempts ?? response.maxAttempts ?? 0));

    result.batchMode = false;
    result.batchResults = [];
    result.batchResultPath = "";
    result.batchProgress.total = 0;
    result.batchProgress.completed = 0;
    result.batchProgress.currentIndex = 0;
    result.batchProgress.currentCase = "";
    result.batchProgress.currentAttempt = 0;
    result.batchProgress.currentAttemptStates = ["", "", "", "", ""];
    result.batchProgress.provedCount = 0;
    result.batchProgress.notProvedCount = 0;
    result.batchProgress.stopCount = 0;
    result.attemptCount = attemptCount;
    result.maxAttempts = maxAttempts;

    result.candidateFunctions = candidateFunction || "模型未返回候选函数，请检查运行日志。";
    result.artifactSummary = finalSummary || rawResponse || "无结果摘要。";
    result.checkerStatus = checkerStatus;
    result.checkerVerdict = checkerVerdict;
    result.checkerConclusion = checkerConclusion;
    result.checkerMessage = checkerMessage;
    result.checkerCounterexample = checkerCounterexample;
    result.checkerRawOutput = checkerRawOutput;
    verification.selectedLine = Math.max(1, Number(verification.selectedLine || 1));
    verification.lineText = resolveLineTextByNumber(form.code, verification.selectedLine);
    await requestVerificationSummary({
      candidateFunction: result.candidateFunctions,
      checkerStatus,
      checkerVerdict,
      checkerConclusion,
      checkerMessage,
      checkerCounterexample,
      selectedLine: verification.selectedLine
    });

    if (status === "SUCCESS") {
    result.checkerFeedback = checkerFeedback || [
      checkerStatus ? `执行状态: ${checkerStatus}` : "",
      checkerVerdict ? `验证结果: ${checkerVerdict}` : "",
      checkerConclusion ? `结论: ${checkerConclusion}` : "",
      attemptCount > 0 ? `尝试轮次: ${maxAttempts > 0 ? `${attemptCount}/${maxAttempts}` : attemptCount}` : "",
      checkerMessageRaw ? `信息: ${checkerMessageRaw}` : "",
      checkerCounterexample ? `反例:\n${checkerCounterexample}` : "",
      checkerRawOutput ? `\n${checkerRawOutput}` : ""
    ].filter(Boolean).join("\n") || "验证器未返回有效输出。";

      if (attemptCount > 0) {
        appendLog(`单程序最终采用第 ${maxAttempts > 0 ? `${attemptCount}/${maxAttempts}` : attemptCount} 轮结果`);
      }
      if (checkerStatus === "COMPLETED" && checkerConclusion === "YES") {
        appendLog("Checker completed: PROVED (YES)");
        ElMessage.success("候选函数生成并通过验证");
      } else if (checkerStatus === "COMPLETED" && checkerConclusion === "NO") {
        appendLog("Checker completed: 验证未通过，已得到反例或无法证明终止");
        ElMessage.warning("Checker completed：候选函数未能证明终止");
      } else if (checkerStatus === "CHECKER_ERROR") {
        appendLog(`生成完成，但验证器执行失败: ${checkerMessageRaw || "未知错误"}`);
        ElMessage.error(checkerMessage || "验证器执行失败，请查看运行日志。");
      } else if (checkerStatus === "SKIPPED") {
        appendLog("生成完成，验证器已跳过");
        ElMessage.warning(checkerMessage || "验证器未执行");
      } else if (checkerStatus === "COMPLETED") {
        appendLog("Checker completed，但未解析到明确 YES/NO 结论");
        ElMessage.warning("Checker completed，但未解析到明确结论");
      } else {
        appendLog("生成完成");
        ElMessage.success("候选函数生成完成");
      }
    } else {
      if (attemptCount > 0 && maxAttempts > 0) {
        appendLog(`单程序已自动尝试 ${attemptCount}/${maxAttempts} 次后仍失败`);
      }
      if (isAffineTemplatePrecheckFailure(checkerMessageRaw) && attemptCount >= Math.max(1, maxAttempts || 3)) {
        result.checkerMessage = `已自动尝试 ${attemptCount} 次，均未生成符合线性模板要求的候选函数。`;
      }
      result.checkerFeedback = checkerFeedback || message || checkerMessage || "模型调用失败，请检查运行日志。";
      appendLog(`生成失败: ${result.checkerFeedback}`);
      const failureSummary = result.checkerMessage
        || summarizeCheckerFacingMessage(result.checkerFeedback)
        || "任务执行失败，请查看运行日志。";
      ElMessage.error(failureSummary);
    }
  } catch (error) {
    if (error?.name === "AbortError") {
      appendLog("验证已暂停：当前请求已中止。");
      result.checkerStatus = "PAUSED";
      result.checkerVerdict = "";
      result.checkerConclusion = "";
      result.checkerMessage = "Verification paused by user.";
      result.checkerFeedback = "Verification paused by user.";
      ElMessage.warning("验证已暂停");
      return;
    }
    const readable = error.message || "调用失败";
    appendLog(`调用失败: ${readable}`);
    result.checkerFeedback = `调用失败：${readable}`;
    result.verificationSummary = buildVerificationSummaryFallback({
      candidateFunction: result.candidateFunctions,
      checkerStatus: result.checkerStatus,
      checkerVerdict: result.checkerVerdict,
      checkerConclusion: result.checkerConclusion,
      checkerMessage: readable,
      selectedLine: verification.selectedLine
    });
    ElMessage.error(summarizeCheckerFacingMessage(readable) || "任务执行失败，请查看运行日志。");
  } finally {
    singleRunAbortController = null;
    if (!keepSubmittingForBatch) {
      submitting.value = false;
    }
  }
};

onMounted(() => {
  window.addEventListener("beforeunload", handleBeforeUnload);
  window.addEventListener("scroll", handleWindowScroll, { passive: true });
  nextTick(() => {
    if (operationRef.value) {
      operationRef.value.addEventListener("scroll", handleOperationScroll, { passive: true });
    }
    if (displayRef.value) {
      displayRef.value.addEventListener("scroll", handleDisplayScroll, { passive: true });
    }
    restoreScrollSnapshot();
    const timeline = gsap.timeline({ defaults: { ease: "power2.out" } });
    timeline
      .fromTo(
        operationRef.value,
        { autoAlpha: 0, y: 18 },
        { autoAlpha: 1, y: 0, duration: 0.46 },
        0.18
      )
      .fromTo(
        displayRef.value,
        { autoAlpha: 0, y: 18 },
        { autoAlpha: 1, y: 0, duration: 0.5 },
        0.34
      );
  });
  if (!runtimeState.ui.mode) {
    runtimeState.ui.mode = "learning";
  }
  verification.selectedLine = Math.max(1, Number(verification.selectedLine || 1));
  verification.lineText = resolveLineTextByNumber(form.code, verification.selectedLine);
  if (String(form.code || "").trim()) {
    if (isLearningMode.value) {
      hydrateLearningFromCode(form.code, { keepLineSelection: true });
      if (!learning.lineExplanation) {
        handleCodeLineSelect({ lineNumber: 1, lineText: String(form.code || "").split("\n")[0] || "" });
      }
    } else if (!result.verificationSummary) {
      result.verificationSummary = buildVerificationSummaryFallback({
        candidateFunction: result.candidateFunctions,
        checkerStatus: result.checkerStatus,
        checkerVerdict: result.checkerVerdict,
        checkerConclusion: result.checkerConclusion,
        checkerMessage: result.checkerMessage,
        selectedLine: verification.selectedLine
      });
    }
  } else {
    if (isLearningMode.value) {
      resetLearningPanels();
    }
  }
  void recoverBatchTaskState();
});

onBeforeUnmount(() => {
  window.removeEventListener("beforeunload", handleBeforeUnload);
  window.removeEventListener("scroll", handleWindowScroll);
  if (operationRef.value) {
    operationRef.value.removeEventListener("scroll", handleOperationScroll);
  }
  if (displayRef.value) {
    displayRef.value.removeEventListener("scroll", handleDisplayScroll);
  }
  updateScrollSnapshot();
  flushPersist();
  clearPendingRouteLeave();
  stopBatchPolling({ clearTaskContext: false });
});

onBeforeRouteLeave((to, from, next) => {
  if (bypassRouteLeaveGuardOnce) {
    bypassRouteLeaveGuardOnce = false;
    next();
    return;
  }

  if (!shouldBlockRouteLeave()) {
    updateScrollSnapshot();
    flushPersist();
    next();
    return;
  }

  if (leaveDialogVisible.value) {
    next(false);
    return;
  }

  pendingRouteLeaveNext = next;
  leaveDialogVisible.value = true;
});
</script>
