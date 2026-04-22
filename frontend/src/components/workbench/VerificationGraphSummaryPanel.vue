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

const parseTokens = (text) => {
  const source = String(text || "");
  const matches = source.match(/[A-Za-z_][A-Za-z0-9_]*/g) || [];
  const stop = new Set([
    "for", "while", "do", "if", "else", "switch", "case", "return",
    "int", "long", "short", "float", "double", "char", "bool", "boolean",
    "void", "class", "public", "private", "protected", "static", "new",
    "true", "false", "null", "and", "or", "not", "max", "min", "relu"
  ]);
  return matches
    .map((item) => item.toLowerCase())
    .filter((item) => !stop.has(item));
};

const parsePositiveGuardTokens = (text) => {
  const source = String(text || "");
  const regex = /([A-Za-z_][A-Za-z0-9_]*)\s*(?:>|>=)\s*(?:0|1)/g;
  const result = new Set();
  let match = regex.exec(source);
  while (match) {
    result.add(String(match[1] || "").toLowerCase());
    match = regex.exec(source);
  }
  return result;
};

const parseAssignmentInfo = (line) => {
  let source = String(line || "").trim();
  if (!source) {
    return null;
  }
  if (source.endsWith(";")) {
    source = source.slice(0, -1).trim();
  }
  let m = source.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*(\+\+|--)$/);
  if (m) {
    return { left: m[1], operator: m[2], right: "1" };
  }
  m = source.match(/^([A-Za-z_][A-Za-z0-9_]*)\s*([+\-*/%])=\s*(.+)$/);
  if (m) {
    return { left: m[1], operator: `${m[2]}=`, right: m[3] };
  }
  m = source.match(/^(?:[A-Za-z_][A-Za-z0-9_<>\[\]]*\s+)*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)$/);
  if (m) {
    return { left: m[1], operator: "=", right: m[2] };
  }
  return null;
};

const looksLikeInputInit = (assignment, line, lastLoopLine) => {
  if (!assignment) {
    return false;
  }
  if (lastLoopLine > 0 && line >= lastLoopLine) {
    return false;
  }
  const rhs = String(assignment.right || "").toLowerCase();
  return rhs.includes("args[")
    || rhs.includes("argv[")
    || rhs.includes("scanner")
    || rhs.includes("stdin")
    || rhs.includes("getline")
    || rhs.includes("readline")
    || rhs.includes("input(");
};

const isPositiveDelta = (delta, positiveTokens) => {
  const value = String(delta || "").toLowerCase().replace(/\s+/g, "");
  if (!value) {
    return false;
  }
  if (/^\d+(\.\d+)?$/.test(value)) {
    return Number(value) > 0;
  }
  return positiveTokens.has(value);
};

const looksLikeSupportUpdate = (assignment, inLoopNeighborhood, loopTokens, positiveTokens) => {
  if (!assignment || !inLoopNeighborhood) {
    return false;
  }
  const lhs = String(assignment.left || "").toLowerCase();
  if (!loopTokens.has(lhs)) {
    return false;
  }
  const op = String(assignment.operator || "");
  if (op === "--") {
    return true;
  }
  if (op === "-=") {
    return isPositiveDelta(assignment.right, positiveTokens);
  }
  if (op !== "=") {
    return false;
  }
  const rhs = String(assignment.right || "").toLowerCase().replace(/\s+/g, "");
  const prefix = `${lhs}-`;
  if (!rhs.startsWith(prefix)) {
    return false;
  }
  return isPositiveDelta(rhs.slice(prefix.length), positiveTokens);
};

const edgeStatusFromNodeStatus = (fromStatus, toStatus) => {
  const a = String(fromStatus || "").toLowerCase();
  const b = String(toStatus || "").toLowerCase();
  if (["unsupport", "high-risk", "risk"].includes(a) || ["unsupport", "high-risk", "risk"].includes(b)) {
    return "risk";
  }
  if (a === "support" || b === "support") {
    return "support";
  }
  if (a === "input" || b === "input") {
    return "highlight";
  }
  return "normal";
};

const fallbackGraph = computed(() => {
  const lines = String(props.code || "").split("\n");
  const nodes = [];
  const loopTokens = new Set();
  const positiveGuardTokens = new Set();
  let lastLoopLine = -1;
  let index = 0;
  lines.forEach((raw, i) => {
    const line = i + 1;
    const trimmed = String(raw || "").trim();
    if (!trimmed || nodes.length >= 10) {
      return;
    }
    let type = "statement";
    let status = "normal";
    let explanation = "基于本地代码结构生成的占位节点。";
    if (/\b(for|while|do)\b/i.test(trimmed)) type = "loop_guard";
    else if (/\b(if|else if|elif|switch|case)\b/i.test(trimmed)) type = "condition";
    else if (/\breturn\b/i.test(trimmed)) type = "return";
    else if (/(?<![=!<>])=(?!=)|\+=|-=|\*=|\/=|%=|\+\+|--/.test(trimmed)) type = "variable_update";

    if (type === "loop_guard") {
      status = "support";
      parseTokens(trimmed).forEach((token) => loopTokens.add(token));
      parsePositiveGuardTokens(trimmed).forEach((token) => positiveGuardTokens.add(token));
      lastLoopLine = line;
      explanation = "循环守卫条件节点，直接影响终止性证明链条。";
    } else if (type === "variable_update" || type === "candidate_update") {
      const assignment = parseAssignmentInfo(trimmed);
      const inLoopNeighborhood = lastLoopLine > 0 && line > lastLoopLine && (line - lastLoopLine) <= 10;
      if (looksLikeInputInit(assignment, line, lastLoopLine)) {
        status = "input";
        explanation = "输入初始化/前置数据供给节点。";
      } else if (looksLikeSupportUpdate(assignment, inLoopNeighborhood, loopTokens, positiveGuardTokens)) {
        status = "support";
        type = "candidate_update";
        explanation = "循环内部直接推动终止性证明的关键更新语句。";
      } else {
        status = "normal";
        explanation = "普通更新节点。";
      }
    }
    nodes.push({
      id: `fallback-${index + 1}`,
      label: trimmed.length > 44 ? `${trimmed.slice(0, 44)}...` : trimmed,
      type,
      lineStart: line,
      lineEnd: line,
      status,
      explanation
    });
    index += 1;
  });
  const edges = [];
  for (let i = 0; i < nodes.length - 1; i += 1) {
    edges.push({
      source: nodes[i].id,
      target: nodes[i + 1].id,
      type: "syntax_flow",
      status: edgeStatusFromNodeStatus(nodes[i].status, nodes[i + 1].status)
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
        status: edgeStatusFromNodeStatus(from.status, to.status)
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
  align-items: stretch;
}

@media (max-width: 1280px) {
  .wb-verification-bottom-grid {
    grid-template-columns: 1fr;
  }
}
</style>
