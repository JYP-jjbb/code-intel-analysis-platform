<template>
  <div class="nutera-workbench">
    <div class="nutera-grid">
      <section ref="operationRef" class="nutera-operation" data-nutera-left>
        <TaskConfigCard
          :form="form"
          :submitting="submitting"
          :run-state="primaryRunState"
          @file-change="handleFileChange"
          @submit="submitTask"
          @pause="pauseTask"
          @reset="resetForm"
        />
      </section>

      <section ref="displayRef" class="nutera-display" data-nutera-right>
        <LogPanel :logs="logs" />
        <SummaryPanel
          :candidate-functions="result.candidateFunctions"
          :checker-status="result.checkerStatus"
          :checker-verdict="result.checkerVerdict"
          :checker-conclusion="result.checkerConclusion"
          :checker-message="result.checkerMessage"
          :checker-counterexample="result.checkerCounterexample"
          :checker-raw-output="result.checkerRawOutput"
          :checker-feedback="result.checkerFeedback"
          :artifact-summary="result.artifactSummary"
          :batch-mode="form.batchMode || result.batchMode"
          :batch-progress="result.batchProgress"
          :batch-results="result.batchResults"
          :batch-result-path="result.batchResultPath"
          :selected-case-key="selectedBatchCaseKey"
          @update:selected-case-key="handleSelectedCaseKeyUpdate"
        />
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
import SummaryPanel from "../components/workbench/SummaryPanel.vue";
import LeaveTaskConfirmDialog from "../components/workbench/LeaveTaskConfirmDialog.vue";
import { fetchBatchStatus, generateRankingFunction, loadCaseSource, pauseBatchTask, startBatchTask } from "../api/nuteraApi.js";
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

const nowLabel = () => new Date().toLocaleString("zh-CN", { hour12: false });

const appendLog = (message) => {
  const line = `[${nowLabel()}] ${message}`;
  logs.value = logs.value ? `${logs.value}\n${line}` : line;
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
  result.checkerCounterexample = "";
  result.checkerRawOutput = "";
  result.checkerFeedback = "";
  result.artifactSummary = "";
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
  const message = sanitizeCheckerUserMessage(snapshot.message || "");
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
  result.checkerStatus = status || "RUNNING";
  result.checkerVerdict = "";
  result.checkerConclusion = "";
  result.checkerMessage = message || "";
  result.checkerCounterexample = "";
  result.checkerRawOutput = "";
  result.checkerFeedback = message || "";
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
    appendLog(`批量处理失败: ${message || "未知错误"}`);
    ElMessage.error(message || "批量处理失败");
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
};

const handleFileChange = (file) => {
  if (!file?.raw) return;
  selectedFileName.value = file.raw.name || "";
  const reader = new FileReader();
  reader.onload = () => {
    form.code = String(reader.result || "");
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

const submitTask = async () => {
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
    const checkerMessage = sanitizeCheckerUserMessage(response.checker_message || response.checkerMessage || "");
    const checkerRawOutput = response.checker_raw_output || response.checkerRawOutput || "";
    const checkerCounterexample = response.checker_counterexample || response.checkerCounterexample || "";
    const checkerFeedback = response.checker_feedback || response.checkerFeedback || "";
    const finalSummary = response.final_summary || response.finalSummary || "";

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

    result.candidateFunctions = candidateFunction || "模型未返回候选函数，请检查运行日志。";
    result.artifactSummary = finalSummary || rawResponse || "无结果摘要。";
    result.checkerStatus = checkerStatus;
    result.checkerVerdict = checkerVerdict;
    result.checkerConclusion = checkerConclusion;
    result.checkerMessage = checkerMessage;
    result.checkerCounterexample = checkerCounterexample;
    result.checkerRawOutput = checkerRawOutput;

    if (status === "SUCCESS") {
      result.checkerFeedback = checkerFeedback || [
        checkerStatus ? `执行状态: ${checkerStatus}` : "",
        checkerVerdict ? `验证结果: ${checkerVerdict}` : "",
        checkerConclusion ? `结论: ${checkerConclusion}` : "",
        checkerMessage ? `信息: ${checkerMessage}` : "",
        checkerCounterexample ? `反例:\n${checkerCounterexample}` : "",
        checkerRawOutput ? `\n${checkerRawOutput}` : ""
      ].filter(Boolean).join("\n") || "验证器未返回有效输出。";

      if (checkerStatus === "COMPLETED" && checkerConclusion === "YES") {
        appendLog("Checker completed: PROVED (YES)");
        ElMessage.success("候选函数生成并通过验证");
      } else if (checkerStatus === "COMPLETED" && checkerConclusion === "NO") {
        appendLog("Checker completed: 验证未通过，已得到反例或无法证明终止");
        ElMessage.warning("Checker completed：候选函数未能证明终止");
      } else if (checkerStatus === "CHECKER_ERROR") {
        appendLog(`生成完成，但验证器执行失败: ${checkerMessage || "未知错误"}`);
        ElMessage.error(checkerMessage || "验证器执行失败");
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
      result.checkerFeedback = checkerFeedback || message || checkerMessage || "模型调用失败，请检查运行日志。";
      appendLog(`生成失败: ${result.checkerFeedback}`);
      ElMessage.error(result.checkerFeedback);
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
    ElMessage.error(readable);
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
