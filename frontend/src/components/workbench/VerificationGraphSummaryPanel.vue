<template>
  <div class="wb-block wb-verification-graph-summary">
    <div class="wb-block-title">验证可视化摘要</div>
    <VerificationGraphCanvas
      :graph="normalizedGraph"
      :selected-line="selectedLine"
      :active-node-id="activeNodeId"
      @node-click="handleNodeClick"
    />

    <div class="wb-verification-bottom-grid">
      <VerificationSlicePanel :slice="displaySlice" :selected-line="selectedLine" />
      <VerificationInsightPanel
        :summary-data="normalizedSummaryData"
        :fallback-summary-text="fallbackSummaryText"
        :fallback-candidate-function="candidateFunctions"
        :fallback-checker-conclusion="checkerConclusion"
        :fallback-verification-status="verificationStatus"
        :fallback-checker-message="checkerMessage"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from "vue";
import VerificationGraphCanvas from "./VerificationGraphCanvas.vue";
import VerificationSlicePanel from "./VerificationSlicePanel.vue";
import VerificationInsightPanel from "./VerificationInsightPanel.vue";

const props = defineProps({
  summaryData: {
    type: Object,
    default: () => ({})
  },
  code: {
    type: String,
    default: ""
  },
  selectedLine: {
    type: Number,
    default: 1
  },
  artifactSummary: {
    type: String,
    default: ""
  },
  candidateFunctions: {
    type: String,
    default: ""
  },
  checkerStatus: {
    type: String,
    default: ""
  },
  checkerVerdict: {
    type: String,
    default: ""
  },
  checkerConclusion: {
    type: String,
    default: ""
  },
  checkerMessage: {
    type: String,
    default: ""
  }
});

const emit = defineEmits(["select-line"]);

const activeNodeId = ref("");

const fallbackGraph = computed(() => {
  const lines = String(props.code || "").split("\n");
  const nodes = [];
  let index = 0;
  lines.forEach((raw, i) => {
    const line = i + 1;
    const trimmed = String(raw || "").trim();
    if (!trimmed || nodes.length >= 10) {
      return;
    }
    let type = "statement";
    if (/\b(for|while|do)\b/i.test(trimmed)) type = "loop_guard";
    else if (/\b(if|else if|elif|switch|case)\b/i.test(trimmed)) type = "condition";
    else if (/\breturn\b/i.test(trimmed)) type = "return";
    else if (/(?<![=!<>])=(?!=)|\+=|-=|\*=|\/=|%=|\+\+|--/.test(trimmed)) type = "variable_update";
    nodes.push({
      id: `fallback-${index + 1}`,
      label: trimmed.length > 44 ? `${trimmed.slice(0, 44)}...` : trimmed,
      type,
      lineStart: line,
      lineEnd: line,
      status: line === Number(props.selectedLine || 1) ? "focus" : "normal",
      explanation: "基于本地代码结构生成的占位节点。"
    });
    index += 1;
  });
  const edges = [];
  for (let i = 0; i < nodes.length - 1; i += 1) {
    edges.push({
      source: nodes[i].id,
      target: nodes[i + 1].id,
      type: "syntax_flow",
      status: "normal"
    });
  }
  for (let i = 0; i < nodes.length; i += 1) {
    const from = nodes[i];
    if (!["loop_guard", "condition"].includes(String(from.type || "").toLowerCase())) {
      continue;
    }
    for (let j = i + 1; j < nodes.length; j += 1) {
      const to = nodes[j];
      const fromLine = Number(from.lineStart || 1);
      const toLine = Number(to.lineStart || 1);
      if (toLine - fromLine > 8) {
        break;
      }
      if (!["variable_update", "candidate_update"].includes(String(to.type || "").toLowerCase())) {
        continue;
      }
      edges.push({
        source: from.id,
        target: to.id,
        type: "control_dep",
        status: "highlight"
      });
    }
  }
  return { nodes, edges };
});

const normalizedSummaryData = computed(() => {
  const source = props.summaryData && typeof props.summaryData === "object" ? props.summaryData : {};
  return {
    summaryText: String(source.summaryText ?? source.summary_text ?? ""),
    verificationStatus: String(source.verificationStatus ?? source.verification_status ?? props.checkerVerdict ?? ""),
    candidateFunction: String(source.candidateFunction ?? source.candidate_function ?? props.candidateFunctions ?? ""),
    focusLines: Array.isArray(source.focusLines ?? source.focus_lines) ? (source.focusLines ?? source.focus_lines) : [],
    graph: source.graph && typeof source.graph === "object" ? source.graph : fallbackGraph.value,
    slice: source.slice && typeof source.slice === "object" ? source.slice : {},
    insight: source.insight && typeof source.insight === "object" ? source.insight : {}
  };
});

const normalizedGraph = computed(() => {
  const graph = normalizedSummaryData.value.graph || {};
  const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
  const edges = Array.isArray(graph.edges) ? graph.edges : [];
  if (nodes.length > 0) {
    return { nodes, edges };
  }
  return fallbackGraph.value;
});

const fallbackSummaryText = computed(() => {
  const rows = [
    `验证状态: ${props.checkerVerdict || props.checkerConclusion || props.checkerStatus || "UNKNOWN"}`,
    `候选函数: ${props.candidateFunctions || "（空）"}`,
    "说明: 当前图为本地结构化占位图，后端可视化摘要接口未返回完整数据时自动启用。"
  ];
  if (String(props.artifactSummary || "").trim()) {
    rows.push(`补充摘要: ${String(props.artifactSummary || "").trim()}`);
  }
  return rows.join("\n");
});

const verificationStatus = computed(() => (
  normalizedSummaryData.value.verificationStatus
  || props.checkerVerdict
  || props.checkerConclusion
  || props.checkerStatus
  || "NOT_PROVED"
));

const displaySlice = computed(() => {
  const slice = normalizedSummaryData.value.slice || {};
  const lines = Array.isArray(slice.lines) ? slice.lines : [];
  const code = String(slice.code || "");
  if (lines.length > 0 && code) {
    return { lines, code };
  }

  const selected = Math.max(1, Number(props.selectedLine || 1));
  const sourceLines = String(props.code || "").split("\n");
  const from = Math.max(1, selected - 1);
  const to = Math.min(sourceLines.length || 1, selected + 1);
  const sliceLines = [];
  const codeRows = [];
  for (let line = from; line <= to; line += 1) {
    sliceLines.push(line);
    codeRows.push(String(sourceLines[line - 1] || ""));
  }
  return { lines: sliceLines, code: codeRows.join("\n") };
});

const resolveNodeBySelectedLine = (lineNumber) => {
  const line = Math.max(1, Number(lineNumber || 1));
  const nodes = Array.isArray(normalizedGraph.value.nodes) ? normalizedGraph.value.nodes : [];
  let matched = null;
  let bestSpan = Number.POSITIVE_INFINITY;
  nodes.forEach((node) => {
    const start = Math.max(1, Number(node.lineStart ?? node.line_start ?? 1));
    const endRaw = Math.max(1, Number(node.lineEnd ?? node.line_end ?? start));
    const end = Math.max(start, endRaw);
    if (line < start || line > end) {
      return;
    }
    const span = Math.max(1, end - start + 1);
    if (span < bestSpan) {
      bestSpan = span;
      matched = String(node.id || "");
    }
  });
  return matched || "";
};

watch(
  () => [props.selectedLine, normalizedGraph.value.nodes],
  () => {
    activeNodeId.value = resolveNodeBySelectedLine(props.selectedLine);
  },
  { immediate: true, deep: true }
);

const handleNodeClick = (node) => {
  activeNodeId.value = String(node?.id || "");
  const line = Math.max(1, Number(node?.lineStart || 1));
  emit("select-line", line);
};
</script>

<style scoped>
.wb-verification-graph-summary {
  margin-bottom: 0;
  display: grid;
  gap: 10px;
}

.wb-verification-bottom-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
}

@media (max-width: 1280px) {
  .wb-verification-bottom-grid {
    grid-template-columns: 1fr;
  }
}
</style>
