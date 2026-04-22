import { reactive } from "vue";

const STORAGE_KEY = "nutera-workspace-runtime-v1";
const LOG_MAX_LENGTH = 400000;
const BATCH_RESULT_LIMIT = 300;
const LEARNING_LINE_EXPLANATION_LIMIT = 1000;
const LEARNING_CODE_BLOCK_LIMIT = 300;

const createDefaultForm = () => ({
  code: "",
  benchmark: "none",
  model: "kimi-k2.5",
  language: "python",
  batchMode: false
});

const createDefaultBatchProgress = () => ({
  total: 0,
  completed: 0,
  currentIndex: 0,
  currentCase: "",
  currentAttempt: 0,
  currentAttemptStates: ["", "", "", "", ""],
  provedCount: 0,
  notProvedCount: 0,
  stopCount: 0
});

const createDefaultResult = () => ({
  candidateFunctions: "",
  checkerStatus: "",
  checkerVerdict: "",
  checkerConclusion: "",
  checkerMessage: "",
  attemptCount: 0,
  maxAttempts: 0,
  checkerCounterexample: "",
  checkerRawOutput: "",
  checkerFeedback: "",
  artifactSummary: "",
  verificationSummary: null,
  batchMode: false,
  batchProgress: createDefaultBatchProgress(),
  batchResults: [],
  batchResultPath: ""
});

const createDefaultCaseSource = () => ({
  dataset: "",
  csvPath: "",
  programPath: "",
  programClass: "",
  programFunction: "",
  entryIndex: 0,
  totalEntries: 0,
  loaded: false,
  loading: false
});

const createDefaultUiState = () => ({
  mode: "learning",
  scrollTopWindow: 0,
  scrollTopOperation: 0,
  scrollTopDisplay: 0
});

const createDefaultVerificationState = () => ({
  selectedLine: 1,
  lineText: ""
});

const createDefaultLearningState = () => ({
  selectedLine: 1,
  lineText: "",
  lineExplanation: "",
  syntaxPoint: "",
  commonMistake: "",
  lineExplanations: [],
  codeBlocks: [],
  selectedBlock: null,
  explainedCodeFingerprint: "",
  steps: [],
  programOutput: "",
  knowledgePoints: [],
  commonMistakes: []
});

const createDefaultState = () => ({
  form: createDefaultForm(),
  selectedFileName: "",
  logs: "",
  result: createDefaultResult(),
  caseSource: createDefaultCaseSource(),
  submitting: false,
  activeBatchTaskId: "",
  lastBatchCompletedCases: 0,
  selectedBatchCaseKey: "",
  verification: createDefaultVerificationState(),
  learning: createDefaultLearningState(),
  ui: createDefaultUiState()
});

const state = reactive(createDefaultState());
let hydrated = false;

const trimText = (value, maxLength) => {
  const text = String(value || "");
  if (text.length <= maxLength) {
    return text;
  }
  return text.slice(text.length - maxLength);
};

const asString = (value, fallback = "") => {
  if (value === null || value === undefined) {
    return fallback;
  }
  return String(value);
};

const asNumber = (value, fallback = 0) => {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
};

const sanitizeCheckerUserMessage = (value) => {
  const source = String(value || "");
  if (!source) {
    return "";
  }
  return source.replace(/^\s*(?:\[checker-runtime-v\d+\]\s*)+/i, "").trim();
};

const normalizeAttemptStates = (value) => {
  const source = Array.isArray(value) ? value : [];
  const states = ["", "", "", "", ""];
  for (let i = 0; i < Math.min(5, source.length); i += 1) {
    states[i] = asString(source[i]).toUpperCase();
  }
  return states;
};

const sanitizeBatchResultRow = (row) => ({
  caseName: asString(row?.caseName ?? row?.case_name),
  candidateFunction: asString(row?.candidateFunction ?? row?.candidate_function),
  executionStatus: asString(row?.executionStatus ?? row?.execution_status),
  attemptCount: asNumber(row?.attemptCount ?? row?.attempt_count),
  finalStatus: asString(row?.finalStatus ?? row?.final_status),
  verificationStatus: asString(row?.verificationStatus ?? row?.verification_status),
  conclusion: asString(row?.conclusion),
  stopReason: asString(row?.stopReason ?? row?.stop_reason),
  message: sanitizeCheckerUserMessage(asString(row?.message)),
  debugMessage: asString(row?.debugMessage ?? row?.debug_message),
  traceTag: asString(row?.traceTag ?? row?.trace_tag),
  counterexample: asString(row?.counterexample),
  checkerFeedback: asString(row?.checkerFeedback ?? row?.checker_feedback),
  checkerRawOutput: asString(row?.checkerRawOutput ?? row?.checker_raw_output),
  attemptStates: normalizeAttemptStates(row?.attemptStates ?? row?.attempt_states)
});

const normalizeBatchResults = (value) => {
  const source = Array.isArray(value) ? value : [];
  return source.slice(0, BATCH_RESULT_LIMIT).map(sanitizeBatchResultRow);
};

const sanitizeVerificationSummary = (value) => {
  if (!value || typeof value !== "object") {
    return null;
  }
  const graph = value.graph && typeof value.graph === "object" ? value.graph : {};
  const slice = value.slice && typeof value.slice === "object" ? value.slice : {};
  const insight = value.insight && typeof value.insight === "object" ? value.insight : {};
  return {
    status: asString(value.status),
    message: asString(value.message),
    summaryText: asString(value.summaryText ?? value.summary_text),
    verificationStatus: asString(value.verificationStatus ?? value.verification_status),
    candidateFunction: asString(value.candidateFunction ?? value.candidate_function),
    focusLines: Array.isArray(value.focusLines ?? value.focus_lines)
      ? (value.focusLines ?? value.focus_lines).slice(0, 120).map((item) => Math.max(1, asNumber(item, 1)))
      : [],
    graph: {
      nodes: Array.isArray(graph.nodes)
        ? graph.nodes.slice(0, 200).map((node, idx) => {
          const lineStart = Math.max(1, asNumber(node?.lineStart ?? node?.line_start, 1));
          const lineEndRaw = Math.max(1, asNumber(node?.lineEnd ?? node?.line_end, lineStart));
          return {
            id: asString(node?.id, `n-${idx + 1}`),
            label: asString(node?.label),
            type: asString(node?.type),
            lineStart,
            lineEnd: Math.max(lineStart, lineEndRaw),
            status: asString(node?.status),
            explanation: asString(node?.explanation)
          };
        })
        : [],
      edges: Array.isArray(graph.edges)
        ? graph.edges.slice(0, 400).map((edge) => ({
          source: asString(edge?.source),
          target: asString(edge?.target),
          type: asString(edge?.type),
          status: asString(edge?.status)
        }))
        : []
    },
    slice: {
      lines: Array.isArray(slice.lines)
        ? slice.lines.slice(0, 120).map((item) => Math.max(1, asNumber(item, 1)))
        : [],
      code: asString(slice.code)
    },
    insight: {
      target: asString(insight.target),
      checkerConclusion: asString(insight.checkerConclusion ?? insight.checker_conclusion),
      proofOutcome: asString(insight.proofOutcome ?? insight.proof_outcome),
      failureReason: asString(insight.failureReason ?? insight.failure_reason),
      highlightExplanation: asString(insight.highlightExplanation ?? insight.highlight_explanation)
    }
  };
};

const sanitizeLearningLineExplanation = (item) => {
  const lineNumber = Math.max(1, asNumber(item?.lineNumber ?? item?.line_number, 1));
  return {
    lineNumber,
    lineText: asString(item?.lineText ?? item?.line_text),
    lineExplanation: asString(item?.lineExplanation ?? item?.line_explanation),
    syntaxPoint: asString(item?.syntaxPoint ?? item?.syntax_point),
    commonMistake: asString(item?.commonMistake ?? item?.common_mistake)
  };
};

const normalizeLearningLineExplanations = (value) => {
  const source = Array.isArray(value) ? value : [];
  return source.slice(0, LEARNING_LINE_EXPLANATION_LIMIT).map(sanitizeLearningLineExplanation);
};

const sanitizeLearningCodeBlock = (item) => {
  const startLine = Math.max(1, asNumber(item?.startLine ?? item?.start_line, 1));
  const endLineRaw = Math.max(1, asNumber(item?.endLine ?? item?.end_line, startLine));
  return {
    startLine,
    endLine: Math.max(startLine, endLineRaw),
    blockTitle: asString(item?.blockTitle ?? item?.block_title),
    blockType: asString(item?.blockType ?? item?.block_type),
    blockExplanation: asString(item?.blockExplanation ?? item?.block_explanation),
    keyPoints: Array.isArray(item?.keyPoints ?? item?.key_points)
      ? (item?.keyPoints ?? item?.key_points).slice(0, 24).map((row) => asString(row))
      : [],
    commonMistakes: Array.isArray(item?.commonMistakes ?? item?.common_mistakes)
      ? (item?.commonMistakes ?? item?.common_mistakes).slice(0, 24).map((row) => asString(row))
      : []
  };
};

const normalizeLearningCodeBlocks = (value) => {
  const source = Array.isArray(value) ? value : [];
  return source.slice(0, LEARNING_CODE_BLOCK_LIMIT).map(sanitizeLearningCodeBlock);
};

const normalizeLearningSelectedBlock = (value) => {
  if (!value || typeof value !== "object") {
    return null;
  }
  return sanitizeLearningCodeBlock(value);
};

const applySnapshot = (snapshot) => {
  const form = snapshot?.form || {};
  Object.assign(state.form, createDefaultForm(), {
    code: asString(form.code),
    benchmark: asString(form.benchmark, "none") || "none",
    model: asString(form.model, "kimi-k2.5") || "kimi-k2.5",
    language: asString(form.language, "python") || "python",
    batchMode: Boolean(form.batchMode)
  });

  state.selectedFileName = asString(snapshot?.selectedFileName);
  state.logs = trimText(snapshot?.logs, LOG_MAX_LENGTH);

  const result = snapshot?.result || {};
  Object.assign(state.result, createDefaultResult(), {
    candidateFunctions: asString(result.candidateFunctions),
    checkerStatus: asString(result.checkerStatus),
    checkerVerdict: asString(result.checkerVerdict),
    checkerConclusion: asString(result.checkerConclusion),
    checkerMessage: sanitizeCheckerUserMessage(asString(result.checkerMessage)),
    attemptCount: Math.max(0, asNumber(result.attemptCount ?? result.attempt_count)),
    maxAttempts: Math.max(0, asNumber(result.maxAttempts ?? result.max_attempts)),
    checkerCounterexample: asString(result.checkerCounterexample),
    checkerRawOutput: asString(result.checkerRawOutput),
    checkerFeedback: asString(result.checkerFeedback),
    artifactSummary: asString(result.artifactSummary),
    verificationSummary: sanitizeVerificationSummary(result.verificationSummary),
    batchMode: Boolean(result.batchMode),
    batchResultPath: asString(result.batchResultPath)
  });

  const progress = result?.batchProgress || {};
  Object.assign(state.result.batchProgress, createDefaultBatchProgress(), {
    total: Math.max(0, asNumber(progress.total)),
    completed: Math.max(0, asNumber(progress.completed)),
    currentIndex: Math.max(0, asNumber(progress.currentIndex)),
    currentCase: asString(progress.currentCase),
    currentAttempt: Math.max(0, asNumber(progress.currentAttempt)),
    currentAttemptStates: normalizeAttemptStates(progress.currentAttemptStates),
    provedCount: Math.max(0, asNumber(progress.provedCount)),
    notProvedCount: Math.max(0, asNumber(progress.notProvedCount)),
    stopCount: Math.max(0, asNumber(progress.stopCount))
  });

  const rows = normalizeBatchResults(result?.batchResults);
  state.result.batchResults.splice(0, state.result.batchResults.length, ...rows);

  const caseSource = snapshot?.caseSource || {};
  Object.assign(state.caseSource, createDefaultCaseSource(), {
    dataset: asString(caseSource.dataset),
    csvPath: asString(caseSource.csvPath),
    programPath: asString(caseSource.programPath),
    programClass: asString(caseSource.programClass),
    programFunction: asString(caseSource.programFunction),
    entryIndex: Math.max(0, asNumber(caseSource.entryIndex)),
    totalEntries: Math.max(0, asNumber(caseSource.totalEntries)),
    loaded: Boolean(caseSource.loaded),
    loading: Boolean(caseSource.loading)
  });

  state.submitting = Boolean(snapshot?.submitting);
  state.activeBatchTaskId = asString(snapshot?.activeBatchTaskId);
  state.lastBatchCompletedCases = Math.max(0, asNumber(snapshot?.lastBatchCompletedCases));
  state.selectedBatchCaseKey = asString(snapshot?.selectedBatchCaseKey);
  const verification = snapshot?.verification || {};
  Object.assign(state.verification, createDefaultVerificationState(), {
    selectedLine: Math.max(1, asNumber(verification.selectedLine, 1)),
    lineText: asString(verification.lineText)
  });

  const learning = snapshot?.learning || {};
  Object.assign(state.learning, createDefaultLearningState(), {
    selectedLine: Math.max(1, asNumber(learning.selectedLine, 1)),
    lineText: asString(learning.lineText),
    lineExplanation: asString(learning.lineExplanation),
    syntaxPoint: asString(learning.syntaxPoint),
    commonMistake: asString(learning.commonMistake),
    lineExplanations: normalizeLearningLineExplanations(learning.lineExplanations ?? learning.line_explanations),
    codeBlocks: normalizeLearningCodeBlocks(learning.codeBlocks ?? learning.code_blocks),
    selectedBlock: normalizeLearningSelectedBlock(learning.selectedBlock ?? learning.selected_block),
    explainedCodeFingerprint: asString(learning.explainedCodeFingerprint ?? learning.explained_code_fingerprint),
    steps: Array.isArray(learning.steps)
      ? learning.steps.slice(0, 24).map((item) => asString(item))
      : [],
    programOutput: asString(learning.programOutput),
    knowledgePoints: Array.isArray(learning.knowledgePoints)
      ? learning.knowledgePoints.slice(0, 24).map((item) => asString(item))
      : [],
    commonMistakes: Array.isArray(learning.commonMistakes)
      ? learning.commonMistakes.slice(0, 24).map((item) => asString(item))
      : []
  });

  const ui = snapshot?.ui || {};
  Object.assign(state.ui, createDefaultUiState(), {
    mode: asString(ui.mode, "learning") === "verification" ? "verification" : "learning",
    scrollTopWindow: Math.max(0, asNumber(ui.scrollTopWindow)),
    scrollTopOperation: Math.max(0, asNumber(ui.scrollTopOperation)),
    scrollTopDisplay: Math.max(0, asNumber(ui.scrollTopDisplay))
  });
};

const buildSnapshot = () => ({
  updatedAt: new Date().toISOString(),
  form: {
    code: asString(state.form.code),
    benchmark: asString(state.form.benchmark, "none") || "none",
    model: asString(state.form.model, "kimi-k2.5"),
    language: asString(state.form.language, "python"),
    batchMode: Boolean(state.form.batchMode)
  },
  selectedFileName: asString(state.selectedFileName),
  logs: trimText(state.logs, LOG_MAX_LENGTH),
  result: {
    candidateFunctions: asString(state.result.candidateFunctions),
    checkerStatus: asString(state.result.checkerStatus),
    checkerVerdict: asString(state.result.checkerVerdict),
    checkerConclusion: asString(state.result.checkerConclusion),
    checkerMessage: sanitizeCheckerUserMessage(asString(state.result.checkerMessage)),
    attemptCount: Math.max(0, asNumber(state.result.attemptCount)),
    maxAttempts: Math.max(0, asNumber(state.result.maxAttempts)),
    checkerCounterexample: asString(state.result.checkerCounterexample),
    checkerRawOutput: asString(state.result.checkerRawOutput),
    checkerFeedback: asString(state.result.checkerFeedback),
    artifactSummary: asString(state.result.artifactSummary),
    verificationSummary: sanitizeVerificationSummary(state.result.verificationSummary),
    batchMode: Boolean(state.result.batchMode),
    batchProgress: {
      total: Math.max(0, asNumber(state.result.batchProgress.total)),
      completed: Math.max(0, asNumber(state.result.batchProgress.completed)),
      currentIndex: Math.max(0, asNumber(state.result.batchProgress.currentIndex)),
      currentCase: asString(state.result.batchProgress.currentCase),
      currentAttempt: Math.max(0, asNumber(state.result.batchProgress.currentAttempt)),
      currentAttemptStates: normalizeAttemptStates(state.result.batchProgress.currentAttemptStates),
      provedCount: Math.max(0, asNumber(state.result.batchProgress.provedCount)),
      notProvedCount: Math.max(0, asNumber(state.result.batchProgress.notProvedCount)),
      stopCount: Math.max(0, asNumber(state.result.batchProgress.stopCount))
    },
    batchResults: normalizeBatchResults(state.result.batchResults),
    batchResultPath: asString(state.result.batchResultPath)
  },
  caseSource: {
    dataset: asString(state.caseSource.dataset),
    csvPath: asString(state.caseSource.csvPath),
    programPath: asString(state.caseSource.programPath),
    programClass: asString(state.caseSource.programClass),
    programFunction: asString(state.caseSource.programFunction),
    entryIndex: Math.max(0, asNumber(state.caseSource.entryIndex)),
    totalEntries: Math.max(0, asNumber(state.caseSource.totalEntries)),
    loaded: Boolean(state.caseSource.loaded),
    loading: Boolean(state.caseSource.loading)
  },
  submitting: Boolean(state.submitting),
  activeBatchTaskId: asString(state.activeBatchTaskId),
  lastBatchCompletedCases: Math.max(0, asNumber(state.lastBatchCompletedCases)),
  selectedBatchCaseKey: asString(state.selectedBatchCaseKey),
  verification: {
    selectedLine: Math.max(1, asNumber(state.verification.selectedLine, 1)),
    lineText: asString(state.verification.lineText)
  },
  learning: {
    selectedLine: Math.max(1, asNumber(state.learning.selectedLine, 1)),
    lineText: asString(state.learning.lineText),
    lineExplanation: asString(state.learning.lineExplanation),
    syntaxPoint: asString(state.learning.syntaxPoint),
    commonMistake: asString(state.learning.commonMistake),
    lineExplanations: normalizeLearningLineExplanations(state.learning.lineExplanations),
    codeBlocks: normalizeLearningCodeBlocks(state.learning.codeBlocks),
    selectedBlock: normalizeLearningSelectedBlock(state.learning.selectedBlock),
    explainedCodeFingerprint: asString(state.learning.explainedCodeFingerprint),
    steps: Array.isArray(state.learning.steps)
      ? state.learning.steps.slice(0, 24).map((item) => asString(item))
      : [],
    programOutput: asString(state.learning.programOutput),
    knowledgePoints: Array.isArray(state.learning.knowledgePoints)
      ? state.learning.knowledgePoints.slice(0, 24).map((item) => asString(item))
      : [],
    commonMistakes: Array.isArray(state.learning.commonMistakes)
      ? state.learning.commonMistakes.slice(0, 24).map((item) => asString(item))
      : []
  },
  ui: {
    mode: asString(state.ui.mode, "learning") === "verification" ? "verification" : "learning",
    scrollTopWindow: Math.max(0, asNumber(state.ui.scrollTopWindow)),
    scrollTopOperation: Math.max(0, asNumber(state.ui.scrollTopOperation)),
    scrollTopDisplay: Math.max(0, asNumber(state.ui.scrollTopDisplay))
  }
});

const hydrate = () => {
  if (hydrated) {
    return;
  }
  hydrated = true;
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return;
    }
    const parsed = JSON.parse(raw);
    applySnapshot(parsed);
  } catch (error) {
    console.warn("[nutera-runtime-store] Failed to hydrate state:", error);
  }
};

const persist = () => {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(buildSnapshot()));
  } catch (error) {
    console.warn("[nutera-runtime-store] Failed to persist state:", error);
  }
};

const clearPersisted = () => {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch (_) {
    // ignore
  }
};

const reset = ({ clearStorage = true } = {}) => {
  applySnapshot(createDefaultState());
  if (clearStorage) {
    clearPersisted();
  }
};

export const useNuteraRuntimeStore = () => ({
  state,
  hydrate,
  persist,
  reset,
  clearPersisted
});
