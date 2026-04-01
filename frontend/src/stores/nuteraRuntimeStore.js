import { reactive } from "vue";

const STORAGE_KEY = "nutera-workspace-runtime-v1";
const LOG_MAX_LENGTH = 400000;
const BATCH_RESULT_LIMIT = 300;

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
  checkerCounterexample: "",
  checkerRawOutput: "",
  checkerFeedback: "",
  artifactSummary: "",
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
  scrollTopWindow: 0,
  scrollTopOperation: 0,
  scrollTopDisplay: 0
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
    checkerCounterexample: asString(result.checkerCounterexample),
    checkerRawOutput: asString(result.checkerRawOutput),
    checkerFeedback: asString(result.checkerFeedback),
    artifactSummary: asString(result.artifactSummary),
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

  const ui = snapshot?.ui || {};
  Object.assign(state.ui, createDefaultUiState(), {
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
    checkerCounterexample: asString(state.result.checkerCounterexample),
    checkerRawOutput: asString(state.result.checkerRawOutput),
    checkerFeedback: asString(state.result.checkerFeedback),
    artifactSummary: asString(state.result.artifactSummary),
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
  ui: {
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
