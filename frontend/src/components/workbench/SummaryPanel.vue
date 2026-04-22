<template>
  <el-card
    class="wb-card wb-summary-card"
    :class="{ 'is-learning-mode': mode === 'learning', 'is-collapsed': collapsed }"
    shadow="never"
  >
    <template #header>
      <div class="wb-card-head wb-card-head-split">
        <h3>{{ panelTitle }}</h3>
        <button
          v-if="showCollapseToggle"
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
      <div class="wb-learning-shell">
        <div class="wb-learning-compare">
          <section class="wb-learning-column">
            <div class="wb-block-title wb-learning-column-title">知识要点</div>
            <div class="wb-learning-list">
              <div
                v-for="(item, index) in normalizedKnowledgePoints"
                :key="`knowledge-${index}`"
                class="wb-learning-list-item"
              >
                <span class="wb-learning-dot" />
                <div class="wb-learning-item-shell" @mouseenter="handleLearningItemHover">
                  <div
                    class="wb-learning-item-scroll"
                    :ref="registerLearningScrollRef"
                    @scroll.passive="handleLearningItemScroll"
                  >
                    {{ item }}
                  </div>
                  <div class="wb-learning-scrollbar-overlay" aria-hidden="true">
                    <span class="wb-learning-scrollbar-thumb" />
                  </div>
                </div>
              </div>
            </div>
          </section>

          <section class="wb-learning-column is-mistake">
            <div class="wb-block-title wb-learning-column-title">易错提醒</div>
            <div class="wb-learning-list">
              <div
                v-for="(item, index) in normalizedCommonMistakes"
                :key="`mistake-${index}`"
                class="wb-learning-list-item is-mistake"
              >
                <span class="wb-learning-dot" />
                <div class="wb-learning-item-shell" @mouseenter="handleLearningItemHover">
                  <div
                    class="wb-learning-item-scroll"
                    :ref="registerLearningScrollRef"
                    @scroll.passive="handleLearningItemScroll"
                  >
                    {{ item }}
                  </div>
                  <div class="wb-learning-scrollbar-overlay" aria-hidden="true">
                    <span class="wb-learning-scrollbar-thumb" />
                  </div>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
      </div><!-- /wb-collapsible-inner -->
      </div><!-- /wb-collapsible-wrap -->
    </template>

    <template v-else>
      <div class="wb-collapsible-wrap" :class="{ 'is-collapsed': collapsed }">
        <div class="wb-collapsible-inner wb-summary-collapsible-inner">
          <template v-if="!batchMode">
            <div class="wb-verification-top-grid">
              <div class="wb-block wb-verification-panel">
                <div class="wb-block-title">候选函数</div>
                <pre class="wb-code-block wb-verification-candidate">{{ candidateFunctions || "候选函数将在任务完成后显示" }}</pre>
              </div>

              <div class="wb-block wb-verification-panel">
                <div class="wb-block-title">Checker 结果</div>
                <div class="wb-verification-checker-scroll">
                  <div class="wb-status-grid" v-if="checkerStatus">
                    <div class="wb-status-item">
                      <div class="wb-status-key">执行状态</div>
                      <div class="wb-status-value">
                        <span :class="statusToneClass">{{ checkerStatus }}</span>
                      </div>
                    </div>
                    <div class="wb-status-item">
                      <div class="wb-status-key">验证状态</div>
                      <div class="wb-status-value">
                        <span :class="verdictToneClass">{{ checkerVerdict || checkerVerdictFallback }}</span>
                      </div>
                    </div>
                    <div class="wb-status-item">
                      <div class="wb-status-key">结论</div>
                      <div class="wb-status-value">
                        <span :class="conclusionToneClass">{{ checkerConclusion || checkerConclusionFallback }}</span>
                      </div>
                    </div>
                    <div class="wb-status-item wb-status-item-full" v-if="checkerMessage">
                      <div class="wb-status-key">说明</div>
                      <div class="wb-status-value">{{ checkerMessage }}</div>
                    </div>
                  </div>
                  <div class="wb-block" v-if="hasCounterexample">
                    <div class="wb-block-title">反例</div>
                    <pre class="wb-code-block">{{ normalizedCounterexample }}</pre>
                  </div>
                  <pre class="wb-code-block" v-if="checkerFeedback">{{ checkerFeedback }}</pre>
                  <div class="wb-code-block" v-else>Checker 输出将在任务完成后显示</div>
                  <details class="wb-raw-details" v-if="checkerRawOutput">
                    <summary>原始 Checker 输出</summary>
                    <pre class="wb-code-block">{{ checkerRawOutput }}</pre>
                  </details>
                </div>
              </div>
            </div>

            <div class="wb-block wb-verification-summary">
              <VerificationGraphSummaryPanel
                :summary-data="verificationSummaryData"
                :code="verificationCode"
                :selected-line="verificationSelectedLine"
                :artifact-summary="artifactSummary"
                :candidate-functions="candidateFunctions"
                :checker-status="checkerStatus"
                :checker-verdict="checkerVerdict"
                :checker-conclusion="checkerConclusion"
                :checker-message="checkerMessage"
                @select-line="handleSelectVerificationLine"
              />
            </div>
          </template>
          <template v-else>
            <div class="wb-block">
              <div class="wb-block-title">批量进度</div>
              <div class="wb-status-grid wb-batch-grid">
                <div class="wb-status-item">
                  <div class="wb-status-key">总案例数</div>
                  <div class="wb-status-value"><span class="progress-pill is-total">{{ batchProgress.total || 0 }}</span></div>
                </div>
                <div class="wb-status-item">
                  <div class="wb-status-key">已完成数</div>
                  <div class="wb-status-value"><span class="progress-pill is-good">{{ batchProgress.completed || 0 }}</span></div>
                </div>
                <div class="wb-status-item">
                  <div class="wb-status-key">PROVED 数</div>
                  <div class="wb-status-value"><span class="progress-pill is-good">{{ batchProgress.provedCount || 0 }}</span></div>
                </div>
                <div class="wb-status-item">
                  <div class="wb-status-key">NOT_PROVED 数</div>
                  <div class="wb-status-value"><span class="progress-pill is-bad">{{ batchProgress.notProvedCount || 0 }}</span></div>
                </div>
                <div class="wb-status-item">
                  <div class="wb-status-key">STOP 数</div>
                  <div class="wb-status-value"><span class="progress-pill is-warn">{{ batchProgress.stopCount || 0 }}</span></div>
                </div>
                <div class="wb-status-item">
                  <div class="wb-status-key">当前处理案例</div>
                  <div class="wb-status-value"><span class="progress-pill is-neutral">{{ batchProgress.currentCase || "-" }}</span></div>
                </div>
                <div class="wb-status-item">
                  <div class="wb-status-key">当前处理次数</div>
                  <div class="wb-status-value"><span class="progress-pill is-neutral">{{ currentAttemptLabel }}</span></div>
                </div>
                <div class="wb-status-item wb-attempt-state-item">
                  <div class="wb-status-key">当前处理情况</div>
                  <div class="wb-status-value">
                    <div class="attempt-circles">
                      <span
                        v-for="(state, idx) in normalizedCurrentAttemptStates"
                        :key="`attempt-${idx}`"
                        class="attempt-circle"
                        :class="attemptCircleClass(state)"
                        :title="`第 ${idx + 1} 轮: ${state || 'PENDING'}`"
                      />
                    </div>
                  </div>
                </div>
              </div>
              <div class="wb-batch-path" v-if="batchResultPath">
                批次结果文件: {{ batchResultPath }}
              </div>
            </div>

            <div class="wb-block">
              <div class="wb-block-title">批量结果列表</div>
              <el-table
                :data="batchResults"
                :row-key="resolveBatchCaseKey"
                :current-row-key="selectedBatchCaseKeyResolved"
                border
                stripe
                style="width: 100%"
                highlight-current-row
                @row-click="handleBatchRowClick"
              >
                <el-table-column prop="caseName" label="案例名" min-width="180" show-overflow-tooltip />
                <el-table-column prop="candidateFunction" label="候选函数" min-width="220" show-overflow-tooltip />
                <el-table-column prop="executionStatus" label="执行状态" min-width="130" />
                <el-table-column prop="attemptCount" label="尝试次数" min-width="120" />
                <el-table-column label="最终状态" min-width="130">
                  <template #default="scope">
                    <span :class="toneClassByFinalStatus(scope.row.finalStatus)">{{ scope.row.finalStatus || "-" }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="验证状态" min-width="140">
                  <template #default="scope">
                    <span :class="toneClassByVerification(scope.row.verificationStatus)">{{ scope.row.verificationStatus }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="结论" min-width="140">
                  <template #default="scope">
                    <span :class="toneClassByConclusion(scope.row.conclusion)">{{ scope.row.conclusion }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="stopReason" label="停止原因" min-width="180" show-overflow-tooltip />
                <el-table-column prop="message" label="说明" min-width="260" show-overflow-tooltip />
              </el-table>
            </div>

            <div class="wb-block" v-if="selectedBatchCase">
              <div class="wb-block-title">案例详情: {{ selectedBatchCase.caseName }}</div>
              <div class="wb-status-grid">
                <div class="wb-status-item">
                  <div class="wb-status-key">执行状态</div>
                  <div class="wb-status-value">{{ selectedBatchCase.executionStatus }}</div>
                </div>
                <div class="wb-status-item">
                  <div class="wb-status-key">尝试次数</div>
                  <div class="wb-status-value">{{ selectedBatchCase.attemptCount || 0 }}</div>
                </div>
                <div class="wb-status-item">
                  <div class="wb-status-key">最终状态</div>
                  <div class="wb-status-value">
                    <span :class="toneClassByFinalStatus(selectedBatchCase.finalStatus)">{{ selectedBatchCase.finalStatus || "-" }}</span>
                  </div>
                </div>
                <div class="wb-status-item">
                  <div class="wb-status-key">验证状态</div>
                  <div class="wb-status-value">
                    <span :class="toneClassByVerification(selectedBatchCase.verificationStatus)">{{ selectedBatchCase.verificationStatus }}</span>
                  </div>
                </div>
                <div class="wb-status-item">
                  <div class="wb-status-key">结论</div>
                  <div class="wb-status-value">
                    <span :class="toneClassByConclusion(selectedBatchCase.conclusion)">{{ selectedBatchCase.conclusion }}</span>
                  </div>
                </div>
                <div class="wb-status-item wb-status-item-full" v-if="selectedBatchCase.stopReason">
                  <div class="wb-status-key">停止原因</div>
                  <div class="wb-status-value">{{ selectedBatchCase.stopReason }}</div>
                </div>
              </div>
              <div class="wb-block">
                <div class="wb-block-title">候选函数</div>
                <pre class="wb-code-block">{{ selectedBatchCase.candidateFunction || "（空）" }}</pre>
              </div>
              <div class="wb-block" v-if="selectedBatchCase.counterexample">
                <div class="wb-block-title">反例</div>
                <pre class="wb-code-block">{{ normalizedBatchCounterexample }}</pre>
              </div>
              <div class="wb-block" v-if="selectedBatchCase.message">
                <div class="wb-block-title">说明</div>
                <pre class="wb-code-block">{{ selectedBatchCase.message }}</pre>
              </div>
              <details class="wb-raw-details" v-if="selectedBatchCase.checkerRawOutput">
                <summary>stdout / stderr</summary>
                <pre class="wb-code-block">{{ selectedBatchCase.checkerRawOutput }}</pre>
              </details>
              <details class="wb-raw-details" v-if="selectedBatchCase.log">
                <summary>流程日志</summary>
                <pre class="wb-code-block">{{ selectedBatchCase.log }}</pre>
              </details>
            </div>
          </template>
        </div>
      </div>
    </template>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ArrowDown } from "@element-plus/icons-vue";
import VerificationGraphSummaryPanel from "./VerificationGraphSummaryPanel.vue";

const props = defineProps({
  mode: { type: String, default: "verification" },
  candidateFunctions: { type: String, default: "" },
  checkerStatus: { type: String, default: "" },
  checkerVerdict: { type: String, default: "" },
  checkerConclusion: { type: String, default: "" },
  checkerMessage: { type: String, default: "" },
  checkerCounterexample: { type: String, default: "" },
  checkerRawOutput: { type: String, default: "" },
  checkerFeedback: { type: String, default: "" },
  artifactSummary: { type: String, default: "" },
  verificationSummaryData: { type: Object, default: () => null },
  verificationCode: { type: String, default: "" },
  verificationSelectedLine: { type: Number, default: 1 },
  batchMode: { type: Boolean, default: false },
  batchProgress: { type: Object, default: () => ({}) },
  batchResults: { type: Array, default: () => [] },
  batchResultPath: { type: String, default: "" },
  selectedCaseKey: { type: String, default: "" },
  knowledgePoints: { type: Array, default: () => [] },
  commonMistakes: { type: Array, default: () => [] },
  collapsed: { type: Boolean, default: false },
  collapsible: { type: Boolean, default: false }
});
const emit = defineEmits(["update:selectedCaseKey", "select-verification-line", "toggle-collapse"]);

const panelTitle = computed(() => (props.mode === "learning" ? "核心要点与易错提醒" : "候选函数与验证器反馈"));
const showCollapseToggle = computed(() => props.mode === "learning" || props.collapsible);
const learningScrollRefs = new Set();
let learningResizeObserver = null;

const updateLearningScrollMetrics = (scroller) => {
  if (!(scroller instanceof HTMLElement)) {
    return;
  }
  const shell = scroller.closest(".wb-learning-item-shell");
  if (!(shell instanceof HTMLElement)) {
    return;
  }

  const clientWidth = Math.max(1, scroller.clientWidth);
  const scrollWidth = Math.max(clientWidth, scroller.scrollWidth);
  const maxScrollLeft = Math.max(0, scrollWidth - clientWidth);
  const isScrollable = maxScrollLeft > 1;
  shell.classList.toggle("is-scrollable", isScrollable);

  if (!isScrollable) {
    shell.style.setProperty("--wb-scroll-thumb-width", "0%");
    shell.style.setProperty("--wb-scroll-thumb-left", "0%");
    return;
  }

  const thumbWidth = Math.max(12, Math.min(100, (clientWidth / scrollWidth) * 100));
  const scrollProgress = maxScrollLeft > 0 ? scroller.scrollLeft / maxScrollLeft : 0;
  const thumbLeft = scrollProgress * (100 - thumbWidth);
  shell.style.setProperty("--wb-scroll-thumb-width", `${thumbWidth}%`);
  shell.style.setProperty("--wb-scroll-thumb-left", `${thumbLeft}%`);
};

const refreshLearningScrollMetrics = () => {
  learningScrollRefs.forEach((el) => updateLearningScrollMetrics(el));
};

const registerLearningScrollRef = (el) => {
  if (!(el instanceof HTMLElement) || learningScrollRefs.has(el)) {
    return;
  }
  learningScrollRefs.add(el);
  if (learningResizeObserver) {
    learningResizeObserver.observe(el);
  }
  nextTick(() => updateLearningScrollMetrics(el));
};

const handleLearningItemScroll = (event) => {
  updateLearningScrollMetrics(event?.target);
};

const handleLearningItemHover = (event) => {
  const shell = event?.currentTarget;
  if (!(shell instanceof HTMLElement)) {
    return;
  }
  const scroller = shell.querySelector(".wb-learning-item-scroll");
  updateLearningScrollMetrics(scroller);
};

const selectedBatchCase = ref(null);
const selectedBatchCaseKeyResolved = computed(() => {
  const key = String(props.selectedCaseKey || "").trim();
  return key || "";
});

const resolveBatchCaseKey = (row, index = -1) => {
  const caseName = String(row?.caseName || "").trim();
  if (caseName) {
    return caseName;
  }
  return index >= 0 ? `idx-${index}` : "";
};

const findBatchCaseByKey = (rows, key) => {
  if (!key) {
    return null;
  }
  for (let i = 0; i < rows.length; i += 1) {
    if (resolveBatchCaseKey(rows[i], i) === key) {
      return rows[i];
    }
  }
  return null;
};

const resolveAutoBatchDetailCase = (rows) => {
  if (!Array.isArray(rows) || rows.length === 0) {
    return null;
  }
  const completedRaw = Number(props.batchProgress?.completed ?? rows.length);
  const completed = Number.isFinite(completedRaw) ? completedRaw : rows.length;
  const preferredIndex = completed > 0
    ? Math.min(rows.length - 1, Math.max(0, completed - 1))
    : rows.length - 1;
  let target = rows[preferredIndex] || rows[rows.length - 1] || null;
  const currentCaseName = String(props.batchProgress?.currentCase || "").trim();
  if (target && currentCaseName && target.caseName === currentCaseName && rows.length > 1) {
    target = rows[Math.max(0, preferredIndex - 1)] || rows[rows.length - 2] || target;
  }
  return target;
};

watch(
  [() => props.batchResults, () => props.batchProgress?.completed, () => props.batchProgress?.currentCase, () => props.batchMode, () => props.selectedCaseKey],
  ([rows]) => {
    if (!props.batchMode) {
      selectedBatchCase.value = null;
      if (props.selectedCaseKey) {
        emit("update:selectedCaseKey", "");
      }
      return;
    }
    if (!Array.isArray(rows) || rows.length === 0) {
      selectedBatchCase.value = null;
      if (props.selectedCaseKey) {
        emit("update:selectedCaseKey", "");
      }
      return;
    }
    const controlledKey = String(props.selectedCaseKey || "").trim();
    const controlledSelected = findBatchCaseByKey(rows, controlledKey);
    if (controlledSelected) {
      selectedBatchCase.value = controlledSelected;
      return;
    }

    const autoSelected = resolveAutoBatchDetailCase(rows);
    selectedBatchCase.value = autoSelected;
    const autoSelectedIndex = rows.indexOf(autoSelected);
    const nextKey = autoSelected ? resolveBatchCaseKey(autoSelected, autoSelectedIndex) : "";
    if (nextKey !== controlledKey) {
      emit("update:selectedCaseKey", nextKey);
    }
  },
  { immediate: true }
);

const handleBatchRowClick = (row) => {
  selectedBatchCase.value = row || null;
  if (!row) {
    emit("update:selectedCaseKey", "");
    return;
  }
  const rows = Array.isArray(props.batchResults) ? props.batchResults : [];
  const rowIndex = rows.indexOf(row);
  emit("update:selectedCaseKey", resolveBatchCaseKey(row, rowIndex));
};

const checkerConclusionFallback = computed(() => {
  if (props.checkerVerdict === "PROVED") return "YES";
  if (props.checkerVerdict === "DISPROVED" || props.checkerVerdict === "NOT_PROVED") return "NO";
  if (props.checkerStatus === "CHECKER_ERROR") return "ERROR";
  if (props.checkerStatus === "SKIPPED") return "SKIPPED";
  return "UNKNOWN";
});

const checkerVerdictFallback = computed(() => {
  if (props.checkerConclusion === "YES") return "PROVED";
  if (props.checkerConclusion === "NO") return "NOT_PROVED";
  return "UNKNOWN";
});

const hasCounterexample = computed(() => {
  return (props.checkerVerdict === "DISPROVED" || props.checkerVerdict === "NOT_PROVED" || props.checkerConclusion === "NO")
    && Boolean(props.checkerCounterexample?.trim());
});

const normalizedCounterexample = computed(() => {
  const text = String(props.checkerCounterexample || "").trim();
  if (!text) return "";
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch (_) {
    return text;
  }
});

const normalizedBatchCounterexample = computed(() => {
  const text = String(selectedBatchCase.value?.counterexample || "").trim();
  if (!text) return "";
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch (_) {
    return text;
  }
});

const normalizedCurrentAttemptStates = computed(() => {
  const source = Array.isArray(props.batchProgress?.currentAttemptStates) ? props.batchProgress.currentAttemptStates : [];
  const states = ["", "", "", "", ""];
  for (let i = 0; i < Math.min(5, source.length); i += 1) {
    states[i] = String(source[i] || "").toUpperCase();
  }
  return states;
});

const normalizedKnowledgePoints = computed(() => {
  if (Array.isArray(props.knowledgePoints) && props.knowledgePoints.length > 0) {
    return props.knowledgePoints;
  }
  return [
    "理解变量初始化与赋值顺序，先赋值后使用。",
    "关注分支与循环的执行条件，避免遗漏边界判断。",
    "输出语句与返回语句共同决定程序可观察行为。"
  ];
});

const normalizedCommonMistakes = computed(() => {
  if (Array.isArray(props.commonMistakes) && props.commonMistakes.length > 0) {
    return props.commonMistakes;
  }
  return [
    "漏写分号、括号或冒号会导致语句结构不完整。",
    "条件表达式写错比较运算符会引发逻辑偏差。",
    "变量在未初始化时直接参与计算或输出。"
  ];
});

const currentAttemptLabel = computed(() => {
  const value = Number(props.batchProgress?.currentAttempt || 0);
  const attempt = value > 0 ? Math.min(5, value) : 0;
  if (attempt <= 0) {
    return "第 0/5 次";
  }
  return `第 ${attempt}/5 次`;
});

const attemptCircleClass = (state) => {
  const normalized = String(state || "").toUpperCase();
  if (normalized === "PROVED") return "is-good";
  if (normalized === "STOP") return "is-warn";
  if (normalized === "NOT_PROVED") return "is-bad";
  return "is-empty";
};

const statusToneClass = computed(() => {
  const isGood = props.checkerStatus === "COMPLETED"
    && (props.checkerConclusion === "YES" || props.checkerVerdict === "PROVED");
  return ["checker-pill", isGood ? "is-good" : "is-bad"];
});

const verdictToneClass = computed(() => {
  const verdict = props.checkerVerdict || checkerVerdictFallback.value;
  return ["checker-pill", verdict === "PROVED" ? "is-good" : "is-bad"];
});

const conclusionToneClass = computed(() => {
  const conclusion = props.checkerConclusion || checkerConclusionFallback.value;
  return ["checker-pill", conclusion === "YES" ? "is-good" : "is-bad"];
});

const toneClassByVerification = (value) => {
  const normalized = String(value || "").toUpperCase();
  if (normalized === "PROVED") return ["checker-pill", "is-good"];
  if (normalized === "STOP") return ["checker-pill", "is-warn"];
  return ["checker-pill", "is-bad"];
};

const toneClassByConclusion = (value) => {
  const normalized = String(value || "").toUpperCase();
  if (normalized === "YES") return ["checker-pill", "is-good"];
  if (normalized === "NO_RELU_SOLUTION") return ["checker-pill", "is-warn"];
  return ["checker-pill", "is-bad"];
};

const toneClassByFinalStatus = (value) => {
  const normalized = String(value || "").toUpperCase();
  if (normalized === "PROVED") return ["checker-pill", "is-good"];
  if (normalized === "STOP") return ["checker-pill", "is-warn"];
  return ["checker-pill", "is-bad"];
};

const handleSelectVerificationLine = (lineNumber) => {
  emit("select-verification-line", Math.max(1, Number(lineNumber || 1)));
};

watch(
  [() => props.mode, normalizedKnowledgePoints, normalizedCommonMistakes],
  async () => {
    await nextTick();
    refreshLearningScrollMetrics();
  },
  { immediate: true }
);

onMounted(() => {
  if (typeof ResizeObserver !== "undefined") {
    learningResizeObserver = new ResizeObserver(() => {
      refreshLearningScrollMetrics();
    });
    learningScrollRefs.forEach((el) => learningResizeObserver.observe(el));
  }
  window.addEventListener("resize", refreshLearningScrollMetrics);
  nextTick(() => refreshLearningScrollMetrics());
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", refreshLearningScrollMetrics);
  if (learningResizeObserver) {
    learningResizeObserver.disconnect();
    learningResizeObserver = null;
  }
  learningScrollRefs.clear();
});
</script>

<style scoped>
.checker-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-weight: 700;
  font-size: 12px;
}

.wb-summary-card {
  font-family: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", "Source Han Sans SC", sans-serif;
}

.checker-pill.is-good {
  color: #0d5e32;
  background: #d9fbe8;
  border: 1px solid #7ad7aa;
}

.checker-pill.is-bad {
  color: #8c1d1d;
  background: #ffe3e3;
  border: 1px solid #f2a4a4;
}

.checker-pill.is-warn {
  color: #8a5a00;
  background: #fff3d6;
  border: 1px solid #f0d08d;
}

.wb-batch-path {
  margin-top: 10px;
  color: #4a4a4a;
  font-size: 12px;
  word-break: break-all;
}

.wb-batch-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.wb-batch-grid > .wb-status-item {
  min-height: 84px;
  align-content: center;
  justify-items: center;
  text-align: center;
}

.wb-batch-grid .wb-status-key,
.wb-batch-grid .wb-status-value {
  text-align: center;
}

.wb-batch-grid .wb-status-value {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
  width: 100%;
}

.wb-attempt-state-item {
  min-height: 84px;
}

.wb-attempt-state-item .wb-status-key,
.wb-attempt-state-item .wb-status-value {
  text-align: center;
}

.wb-attempt-state-item .wb-status-value {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 44px;
}

.progress-pill {
  display: inline-block;
  min-width: 46px;
  text-align: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-weight: 700;
}

.progress-pill.is-total {
  color: #184d8c;
  background: #e6f1ff;
  border: 1px solid #accfff;
}

.progress-pill.is-good {
  color: #0d5e32;
  background: #e4f7ee;
  border: 1px solid #9ed7bb;
}

.progress-pill.is-bad {
  color: #8c1d1d;
  background: #fdeaea;
  border: 1px solid #f2b1b1;
}

.progress-pill.is-warn {
  color: #8a5a00;
  background: #fff3d6;
  border: 1px solid #f0d08d;
}

.progress-pill.is-neutral {
  color: #40566f;
  background: #eef2f7;
  border: 1px solid #c9d3df;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.attempt-circles {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: min(220px, 100%);
  margin: 0 auto;
}

.attempt-circle {
  width: 16px;
  height: 16px;
  border-radius: 999px;
  border: 2px solid #c8d4e3;
  background: #f4f7fb;
}

.attempt-circle.is-good {
  background: #30a46c;
  border-color: #30a46c;
}

.attempt-circle.is-bad {
  background: #d63b3b;
  border-color: #d63b3b;
}

.attempt-circle.is-warn {
  background: #d4a106;
  border-color: #d4a106;
}

.attempt-circle.is-empty {
  background: #f4f7fb;
  border-color: #c8d4e3;
}

.wb-summary-card.is-learning-mode :deep(.el-card__body) {
  overflow-x: hidden;
  overflow-y: auto;
}

/*
 * 验证模式：正文区在 .el-card__body 上整体滚动（标题栏固定）。
 * 之前 .wb-collapsible-wrap 使用 flex:1 + 内层 overflow:hidden + height:100%，
 * 溢出被裁切在内部，body 的 scrollHeight 永远等于可视高度，最外层纵向滚动条不会出现。
 */
.wb-summary-card:not(.is-learning-mode) :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: transparent transparent;
}

.wb-summary-card:not(.is-learning-mode) :deep(.el-card__body):hover {
  scrollbar-color: rgba(68, 125, 207, 0.3) transparent;
}

.wb-summary-card:not(.is-learning-mode) :deep(.el-card__body)::-webkit-scrollbar {
  width: 5px;
}

.wb-summary-card:not(.is-learning-mode) :deep(.el-card__body)::-webkit-scrollbar-track {
  background: transparent;
}

.wb-summary-card:not(.is-learning-mode) :deep(.el-card__body)::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: transparent;
  transition: background-color 0.2s ease;
}

.wb-summary-card:not(.is-learning-mode) :deep(.el-card__body):hover::-webkit-scrollbar-thumb {
  background: rgba(68, 125, 207, 0.3);
}

.wb-summary-card:not(.is-learning-mode) .wb-collapsible-wrap {
  flex: 0 1 auto;
  min-height: 0;
  overflow: visible;
}

.wb-summary-card:not(.is-learning-mode) .wb-collapsible-wrap.is-collapsed {
  overflow: hidden;
}

.wb-summary-collapsible-inner {
  min-height: 0;
  height: auto;
  overflow: visible;
  display: flex;
  flex-direction: column;
}

.wb-learning-shell {
  width: 100%;
  min-width: 0;
  overflow-x: hidden;
}

.wb-learning-compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.wb-learning-column {
  min-width: 0;
  width: 100%;
  display: grid;
  gap: 8px;
  overflow-x: hidden;
  align-content: start;
}

.wb-learning-column-title {
  margin: 0;
  padding: 0;
  line-height: 1.4;
}

.wb-learning-list {
  display: grid;
  gap: 10px;
  overflow-x: hidden;
  align-content: start;
  margin: 0;
  padding: 0;
}

.wb-learning-list-item {
  display: grid;
  grid-template-columns: 8px minmax(0, 1fr);
  column-gap: 8px;
  align-items: center;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  height: 52px;
  min-height: 52px;
  max-height: 52px;
  padding: 8px 12px;
  border-radius: 12px;
  border: 1px solid rgba(44, 107, 255, 0.14);
  background: #f8fbff;
  color: #234b76;
  line-height: 1.5;
  font-size: 12px;
  overflow: hidden;
}

.wb-learning-list-item.is-mistake {
  border-color: rgba(234, 106, 118, 0.2);
  background: #fff7f8;
  color: #87424b;
}

.wb-learning-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  margin: 0;
  flex: 0 0 8px;
  background: #4f8bd6;
  align-self: center;
}

.wb-learning-column.is-mistake .wb-learning-dot {
  background: #de7683;
}

.wb-learning-item-shell {
  width: 100%;
  min-width: 0;
  height: 100%;
  display: flex;
  align-items: center;
  overflow: hidden;
  position: relative;
  --wb-scroll-thumb-width: 0%;
  --wb-scroll-thumb-left: 0%;
}

.wb-learning-item-scroll {
  flex: 1 1 auto;
  width: 100%;
  min-width: 0;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  white-space: nowrap;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-behavior: smooth;
  scrollbar-width: none;
  scrollbar-color: transparent transparent;
  -ms-overflow-style: none;
  line-height: 1.5;
  box-sizing: border-box;
  text-align: left;
  padding: 0;
  margin: 0;
}

.wb-learning-item-scroll::-webkit-scrollbar {
  height: 0;
  width: 0;
}

.wb-learning-item-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.wb-learning-item-scroll::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 999px;
}

.wb-learning-scrollbar-overlay {
  position: absolute;
  left: 2px;
  right: 2px;
  bottom: 2px;
  height: 2px;
  border-radius: 999px;
  background: rgba(52, 117, 201, 0.08);
  opacity: 0;
  transition: opacity 0.18s ease;
  pointer-events: none;
}

.wb-learning-scrollbar-thumb {
  position: absolute;
  top: 0;
  left: var(--wb-scroll-thumb-left);
  width: var(--wb-scroll-thumb-width);
  height: 100%;
  border-radius: 999px;
  background: rgba(52, 117, 201, 0.26);
}

.wb-learning-item-shell.is-scrollable:hover .wb-learning-scrollbar-overlay {
  opacity: 1;
}

.wb-learning-item-shell:not(.is-scrollable) .wb-learning-scrollbar-overlay {
  opacity: 0;
  background: transparent;
}

.wb-verification-top-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  align-items: stretch;
  margin-bottom: 10px;
}

.wb-verification-panel {
  margin-bottom: 0;
  height: 104px;
  min-height: 104px;
  max-height: 104px;
  display: flex;
  flex-direction: column;
}

.wb-verification-candidate {
  flex: 1;
  min-height: 0;
  max-height: none;
  overflow: auto;
}

.wb-verification-checker-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: grid;
  gap: 8px;
  padding-right: 2px;
}

.wb-verification-checker-scroll .wb-block {
  margin-bottom: 0;
}

.wb-verification-checker-scroll .wb-code-block {
  min-height: 52px;
  max-height: none;
}

.wb-verification-summary {
  margin-bottom: 0;
}

.wb-verification-summary-code {
  min-height: 780px;
  max-height: none;
  overflow: auto;
}

@media (max-width: 1200px) {
  .wb-batch-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .wb-verification-top-grid {
    grid-template-columns: 1fr;
  }

  .wb-verification-panel {
    height: 132px;
    min-height: 132px;
    max-height: 132px;
  }

  .wb-learning-compare {
    grid-template-columns: 1fr;
  }
}
</style>




