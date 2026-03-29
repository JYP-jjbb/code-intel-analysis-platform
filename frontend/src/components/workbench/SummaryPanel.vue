<template>
  <el-card class="wb-card wb-summary-card" shadow="never">
    <template #header>
      <div class="wb-card-head">
        <h3>候选函数与验证器反馈</h3>
      </div>
    </template>

    <template v-if="!batchMode">
      <div class="wb-block">
        <div class="wb-block-title">候选函数</div>
        <pre class="wb-code-block">{{ candidateFunctions || "候选函数将在任务完成后显示" }}</pre>
      </div>

      <div class="wb-block">
        <div class="wb-block-title">Checker 结果</div>
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

      <div class="wb-block">
        <div class="wb-block-title">结果摘要</div>
        <pre class="wb-code-block">{{ artifactSummary || "结果摘要将在任务完成后显示" }}</pre>
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
          <el-table-column prop="caseName" label="caseName" min-width="180" show-overflow-tooltip />
          <el-table-column prop="candidateFunction" label="candidateFunction" min-width="220" show-overflow-tooltip />
          <el-table-column prop="executionStatus" label="executionStatus" min-width="130" />
          <el-table-column prop="attemptCount" label="attemptCount" min-width="120" />
          <el-table-column label="finalStatus" min-width="130">
            <template #default="scope">
              <span :class="toneClassByFinalStatus(scope.row.finalStatus)">{{ scope.row.finalStatus || "-" }}</span>
            </template>
          </el-table-column>
          <el-table-column label="verificationStatus" min-width="140">
            <template #default="scope">
              <span :class="toneClassByVerification(scope.row.verificationStatus)">{{ scope.row.verificationStatus }}</span>
            </template>
          </el-table-column>
          <el-table-column label="conclusion" min-width="140">
            <template #default="scope">
              <span :class="toneClassByConclusion(scope.row.conclusion)">{{ scope.row.conclusion }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="stopReason" label="stopReason" min-width="180" show-overflow-tooltip />
          <el-table-column prop="message" label="message" min-width="260" show-overflow-tooltip />
        </el-table>
      </div>

      <div class="wb-block" v-if="selectedBatchCase">
        <div class="wb-block-title">案例详情: {{ selectedBatchCase.caseName }}</div>
        <div class="wb-status-grid">
          <div class="wb-status-item">
            <div class="wb-status-key">executionStatus</div>
            <div class="wb-status-value">{{ selectedBatchCase.executionStatus }}</div>
          </div>
          <div class="wb-status-item">
            <div class="wb-status-key">attemptCount</div>
            <div class="wb-status-value">{{ selectedBatchCase.attemptCount || 0 }}</div>
          </div>
          <div class="wb-status-item">
            <div class="wb-status-key">finalStatus</div>
            <div class="wb-status-value">
              <span :class="toneClassByFinalStatus(selectedBatchCase.finalStatus)">{{ selectedBatchCase.finalStatus || "-" }}</span>
            </div>
          </div>
          <div class="wb-status-item">
            <div class="wb-status-key">verificationStatus</div>
            <div class="wb-status-value">
              <span :class="toneClassByVerification(selectedBatchCase.verificationStatus)">{{ selectedBatchCase.verificationStatus }}</span>
            </div>
          </div>
          <div class="wb-status-item">
            <div class="wb-status-key">conclusion</div>
            <div class="wb-status-value">
              <span :class="toneClassByConclusion(selectedBatchCase.conclusion)">{{ selectedBatchCase.conclusion }}</span>
            </div>
          </div>
          <div class="wb-status-item wb-status-item-full" v-if="selectedBatchCase.stopReason">
            <div class="wb-status-key">stopReason</div>
            <div class="wb-status-value">{{ selectedBatchCase.stopReason }}</div>
          </div>
        </div>
        <div class="wb-block">
          <div class="wb-block-title">候选函数</div>
          <pre class="wb-code-block">{{ selectedBatchCase.candidateFunction || "(empty)" }}</pre>
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
  </el-card>
</template>

<script setup>
import { computed, ref, watch } from "vue";

const props = defineProps({
  candidateFunctions: { type: String, default: "" },
  checkerStatus: { type: String, default: "" },
  checkerVerdict: { type: String, default: "" },
  checkerConclusion: { type: String, default: "" },
  checkerMessage: { type: String, default: "" },
  checkerCounterexample: { type: String, default: "" },
  checkerRawOutput: { type: String, default: "" },
  checkerFeedback: { type: String, default: "" },
  artifactSummary: { type: String, default: "" },
  batchMode: { type: Boolean, default: false },
  batchProgress: { type: Object, default: () => ({}) },
  batchResults: { type: Array, default: () => [] },
  batchResultPath: { type: String, default: "" },
  selectedCaseKey: { type: String, default: "" }
});
const emit = defineEmits(["update:selectedCaseKey"]);

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
</script>

<style scoped>
.checker-pill {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-weight: 700;
  font-size: 12px;
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.wb-batch-grid > .wb-status-item {
  min-height: 84px;
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
</style>
