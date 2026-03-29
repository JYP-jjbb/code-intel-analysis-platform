<template>
  <el-card class="wb-card rp-card" shadow="never">
    <template #header>
      <div class="wb-card-head">
        <h3>报告中心</h3>
        <p>统一查看局部代码分析与工程代码审查任务报告</p>
      </div>
    </template>

    <div class="wb-action-row rp-action-row">
      <el-button type="primary" :loading="loading" @click="loadReports">刷新</el-button>
      <div ref="filterBarRef" class="rp-action-right">
        <el-select v-model="selectedType" class="rp-filter-control" size="small" placeholder="类型筛选">
          <el-option v-for="item in typeFilterOptions" :key="item.value || 'all'" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="selectedStatus" class="rp-filter-control" size="small" placeholder="状态筛选">
          <el-option v-for="item in statusFilterOptions" :key="item.value || 'all'" :label="item.label" :value="item.value" />
        </el-select>
        <el-select
          v-model="selectedModel"
          class="rp-filter-control rp-model-filter-control"
          size="small"
          placeholder="模型筛选"
          filterable
          clearable
        >
          <el-option v-for="item in modelFilterOptions" :key="item.value || 'all'" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="taskKeyword" class="rp-search-control" size="small" clearable placeholder="搜索任务名称">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
    </div>

    <div ref="tableWrapRef" class="rp-table-wrap">
      <el-table :data="pagedRows" border stripe style="width: 100%" table-layout="fixed">
      <el-table-column label="序号" width="72" align="center">
        <template #default="{ row }">{{ row.serialNo }}</template>
      </el-table-column>

      <el-table-column label="类型" width="110" align="center">
        <template #default="{ row }">
          <span class="rp-type-pill" :class="row.typeCode === 'CODE_REVIEW' ? 'is-review' : 'is-local'">
            {{ row.typeLabel }}
          </span>
        </template>
      </el-table-column>

      <el-table-column label="任务名称" width="170" header-align="left" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="rp-task-name-wrap">
            <div class="rp-task-name-main" :title="row.taskNameRaw || row.taskNameDisplay">{{ row.taskNameDisplay }}</div>
            <div class="rp-task-name-sub" v-if="row.taskNameRaw && row.taskNameRaw !== row.taskNameDisplay" :title="row.taskNameRaw">
              {{ row.taskNameRaw }}
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="调用模型" min-width="234" align="center" header-align="center" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="rp-model-cell" :title="row.modelRaw || row.modelDisplay">{{ row.modelDisplay || "-" }}</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="114" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)">
            {{ normalizeStatus(row.status) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="startedAtText" label="开始时间" min-width="168" align="center" />
      <el-table-column prop="finishedAtText" label="结束时间" min-width="168" align="center" />

      <el-table-column label="操作" min-width="208" align="center" fixed="right">
        <template #default="{ row }">
          <div class="rp-op-group">
            <el-tooltip content="统计预览" placement="top">
              <el-button class="rp-op-btn" circle :disabled="!row.supportsBatchOps" @click.stop="openStats(row)">
                <svg class="rp-op-icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M12 3a9 9 0 1 0 9 9h-9V3Zm2 0v7h7A9 9 0 0 0 14 3Z"
                    fill="currentColor"
                  />
                </svg>
              </el-button>
            </el-tooltip>

            <el-tooltip content="明细预览" placement="top">
              <el-button class="rp-op-btn" circle :disabled="!row.supportsBatchOps" @click.stop="openCaseTable(row)">
                <svg class="rp-op-icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M4 4h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Zm0 4v3h16V8H4Zm0 4v3h5v-3H4Zm7 0v3h9v-3h-9Z"
                    fill="currentColor"
                  />
                </svg>
              </el-button>
            </el-tooltip>

            <el-tooltip content="导出 CSV" placement="top">
              <el-button class="rp-op-btn" circle :disabled="!row.supportsBatchOps" @click.stop="exportCases(row)">
                <svg class="rp-op-icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M11 3h2v9.17l2.59-2.58L17 11l-5 5-5-5 1.41-1.41L11 12.17V3Zm-7 14h16v4H4v-4Z"
                    fill="currentColor"
                  />
                </svg>
              </el-button>
            </el-tooltip>

            <el-tooltip content="删除报告" placement="top">
              <el-button class="rp-op-btn is-danger" circle :disabled="!row.supportsBatchOps" @click.stop="deleteReport(row)">
                <svg class="rp-op-icon" viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M8 4h8l1 2h4v2H3V6h4l1-2Zm-2 6h12l-1 10a1 1 0 0 1-1 .9H8a1 1 0 0 1-1-.9L6 10Zm3 2v6h2v-6H9Zm4 0v6h2v-6h-2Z"
                    fill="currentColor"
                  />
                </svg>
              </el-button>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
        <template #empty>
          <div class="rp-empty-wrap">
            <el-empty :description="tableEmptyDescription" />
          </div>
        </template>
      </el-table>
    </div>

    <div class="rp-pagination-wrap">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="filteredRows.length"
        :page-size="pageSize"
        :current-page="currentPage"
        @current-change="handlePageChange"
      />
    </div>
  </el-card>

  <el-dialog v-model="statsVisible" title="报告统计预览" width="760px" destroy-on-close>
    <template v-if="statsLoading">
      <div class="rp-loading-row">正在加载统计信息...</div>
    </template>
    <template v-else-if="statsReport">
      <div class="rp-summary-grid">
        <div class="rp-summary-item">
          <span>任务名称</span>
          <strong>{{ statsReport.taskNameDisplay || statsReport.datasetName || "-" }}</strong>
        </div>
        <div class="rp-summary-item">
          <span>任务状态</span>
          <strong>{{ normalizeStatus(statsReport.status) }}</strong>
        </div>
        <div class="rp-summary-item">
          <span>总案例数</span>
          <strong>{{ safeInt(statsReport.totalCases) }}</strong>
        </div>
        <div class="rp-summary-item">
          <span>已完成</span>
          <strong>{{ safeInt(statsReport.completedCases) }}</strong>
        </div>
        <div class="rp-summary-item">
          <span>开始时间</span>
          <strong>{{ formatDateTime(statsReport.startedAt) }}</strong>
        </div>
        <div class="rp-summary-item">
          <span>结束时间</span>
          <strong>{{ formatDateTime(statsReport.finishedAt) }}</strong>
        </div>
      </div>

      <div class="rp-chart-panel">
        <div class="rp-donut-box">
          <div class="rp-donut" :style="donutStyle">
            <div class="rp-donut-hole">
              <span>{{ completionRate }}%</span>
              <em>完成率</em>
            </div>
          </div>
        </div>

        <div class="rp-legend">
          <div v-for="item in statLegend" :key="item.key" class="rp-legend-item">
            <span class="rp-legend-dot" :style="{ backgroundColor: item.color }"></span>
            <span class="rp-legend-label">{{ item.label }}</span>
            <strong class="rp-legend-value">{{ item.value }}</strong>
          </div>
        </div>
      </div>
    </template>
  </el-dialog>

  <el-dialog v-model="casesVisible" title="Case 明细预览" width="88%" destroy-on-close>
    <div class="rp-case-head">
      <div><strong>任务名称:</strong> {{ caseReport.taskNameDisplay || caseReport.datasetName || "-" }}</div>
      <div><strong>状态:</strong> {{ normalizeStatus(caseReport.status) }}</div>
      <div><strong>总案例数:</strong> {{ safeInt(caseReport.totalCases) }}</div>
    </div>

    <el-table v-loading="casesLoading" :data="caseRows" border stripe style="width: 100%">
      <el-table-column label="序号" width="68" align="center">
        <template #default="{ $index }">{{ $index + 1 }}</template>
      </el-table-column>
      <el-table-column prop="caseName" label="caseName" min-width="180" show-overflow-tooltip />
      <el-table-column prop="candidateFunction" label="candidateFunction" min-width="260" show-overflow-tooltip />
      <el-table-column prop="finalStatus" label="finalStatus" min-width="120" />
      <el-table-column prop="conclusion" label="conclusion" min-width="100" />
      <el-table-column prop="message" label="message" min-width="260" show-overflow-tooltip />
    </el-table>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search } from "@element-plus/icons-vue";
import gsap from "gsap";
import {
  deleteBatchReport,
  exportBatchReportCsv,
  fetchBatchReportCases,
  fetchBatchReportDetail,
  fetchBatchReports,
  fetchTaskLogs
} from "../api/nuteraApi.js";
import { fetchTaskList } from "../api/taskApi.js";

const loading = ref(false);
const reports = ref([]);

const statsVisible = ref(false);
const statsLoading = ref(false);
const statsReport = ref(null);

const casesVisible = ref(false);
const casesLoading = ref(false);
const caseReport = ref({});
const caseRows = ref([]);

const filterBarRef = ref(null);
const tableWrapRef = ref(null);

const selectedType = ref("");
const selectedStatus = ref("");
const selectedModel = ref("");
const taskKeyword = ref("");

const currentPage = ref(1);
const pageSize = 10;

const typeFilterOptions = [
  { label: "全部类型", value: "" },
  { label: "局部代码", value: "NUTERA" },
  { label: "工程代码", value: "CODE_REVIEW" }
];

const MODEL_FILTER_KEYS = {
  DEEPSEEK: "DEEPSEEK",
  HUNYUAN: "HUNYUAN",
  KIMI: "KIMI",
  OTHER: "OTHER"
};

const reportRows = computed(() =>
  (Array.isArray(reports.value) ? reports.value : []).map((item) => {
    const startedAtText = formatDateTime(item.startedAt);
    const useModelFallback =
      startedAtText === "2026-03-28 23:42:48" &&
      !String(item.modelRaw || "").trim() &&
      (!item.modelDisplay || item.modelDisplay === "-");
    return {
      ...item,
      modelDisplay: useModelFallback ? "Pro/moonshotai/Kimi-K2.5" : item.modelDisplay,
      modelRaw: useModelFallback ? "Pro/moonshotai/Kimi-K2.5" : item.modelRaw,
      startedAtText,
      finishedAtText: formatDateTime(item.finishedAt)
    };
  })
);

const statusFilterOptions = computed(() => {
  const set = new Set();
  for (const row of reportRows.value) {
    const status = normalizeStatus(row.status);
    if (status && status !== "-") {
      set.add(status);
    }
  }
  return [
    { label: "全部状态", value: "" },
    ...Array.from(set).sort().map((value) => ({ label: value, value }))
  ];
});

const modelFilterOptions = computed(() => {
  const counters = {
    [MODEL_FILTER_KEYS.DEEPSEEK]: 0,
    [MODEL_FILTER_KEYS.HUNYUAN]: 0,
    [MODEL_FILTER_KEYS.KIMI]: 0,
    [MODEL_FILTER_KEYS.OTHER]: 0
  };
  for (const row of reportRows.value) {
    const key = classifyModelKey(row.modelRaw || row.modelDisplay);
    counters[key] += 1;
  }
  return [
    { label: `全部模型 (${reportRows.value.length})`, value: "" },
    { label: `DeepSeek (${counters[MODEL_FILTER_KEYS.DEEPSEEK]})`, value: MODEL_FILTER_KEYS.DEEPSEEK },
    { label: `Hunyuan (${counters[MODEL_FILTER_KEYS.HUNYUAN]})`, value: MODEL_FILTER_KEYS.HUNYUAN },
    { label: `Kimi (${counters[MODEL_FILTER_KEYS.KIMI]})`, value: MODEL_FILTER_KEYS.KIMI },
    { label: `Other (${counters[MODEL_FILTER_KEYS.OTHER]})`, value: MODEL_FILTER_KEYS.OTHER }
  ];
});

const filteredRows = computed(() => {
  const keyword = String(taskKeyword.value || "").trim().toLowerCase();
  return reportRows.value.filter((row) => {
    if (selectedType.value && row.typeCode !== selectedType.value) {
      return false;
    }
    if (selectedStatus.value && normalizeStatus(row.status) !== selectedStatus.value) {
      return false;
    }
    if (selectedModel.value) {
      const key = classifyModelKey(row.modelRaw || row.modelDisplay);
      if (key !== selectedModel.value) {
        return false;
      }
    }
    if (keyword) {
      const target = `${row.taskNameDisplay || ""} ${row.taskNameRaw || ""}`.toLowerCase();
      if (!target.includes(keyword)) {
        return false;
      }
    }
    return true;
  });
});

const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredRows.value.slice(start, start + pageSize).map((item, index) => ({
    ...item,
    serialNo: start + index + 1
  }));
});

const hasActiveFilter = computed(() =>
  Boolean(
    selectedType.value ||
    selectedStatus.value ||
    selectedModel.value ||
    String(taskKeyword.value || "").trim()
  )
);

const tableEmptyDescription = computed(() =>
  hasActiveFilter.value ? "暂无匹配结果，请调整筛选或搜索条件" : "暂无报告数据"
);

const statLegend = computed(() => {
  const report = statsReport.value;
  if (!report) return [];
  const total = safeInt(report.totalCases);
  const proved = safeInt(report.provedCount);
  const notProved = safeInt(report.notProvedCount);
  const stop = safeInt(report.stopCount);
  const error = safeInt(report.errorCount);
  const known = proved + notProved + stop + error;
  const pending = Math.max(total - known, 0);
  return [
    { key: "proved", label: "PROVED", value: proved, color: "#34b48a" },
    { key: "notProved", label: "NOT_PROVED", value: notProved, color: "#eb5a68" },
    { key: "stop", label: "STOP", value: stop, color: "#f2bf43" },
    { key: "error", label: "ERROR", value: error, color: "#f29d4b" },
    { key: "pending", label: "PENDING", value: pending, color: "#d7dfeb" }
  ];
});

const donutStyle = computed(() => {
  const entries = statLegend.value.filter((item) => item.value > 0);
  if (!entries.length) {
    return { background: "conic-gradient(#d7dfeb 0deg 360deg)" };
  }
  const total = entries.reduce((sum, item) => sum + item.value, 0);
  let angle = 0;
  const sectors = entries.map((item) => {
    const start = angle;
    angle += (item.value / total) * 360;
    return `${item.color} ${start}deg ${angle}deg`;
  });
  return { background: `conic-gradient(${sectors.join(",")})` };
});

const completionRate = computed(() => {
  const report = statsReport.value;
  if (!report) return 0;
  const total = safeInt(report.totalCases);
  if (total <= 0) return 0;
  return Math.min(100, Math.round((safeInt(report.completedCases) / total) * 100));
});

const safeInt = (value) => {
  const number = Number(value ?? 0);
  return Number.isFinite(number) ? number : 0;
};

const pad2 = (value) => String(value).padStart(2, "0");

const formatDateTime = (value) => {
  if (!value) return "-";
  const date = new Date(value);
  if (!Number.isNaN(date.getTime())) {
    return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`;
  }
  return String(value)
    .replace("T", " ")
    .replace("Z", "")
    .replace(/\.\d+/, "")
    .slice(0, 19);
};

const normalizeStatus = (value) => {
  const normalized = String(value || "").toUpperCase();
  if (!normalized) return "-";
  if (normalized === "SUCCESS") return "COMPLETED";
  return normalized;
};

const statusTagType = (value) => {
  const normalized = normalizeStatus(value);
  if (normalized === "COMPLETED") return "success";
  if (normalized === "RUNNING" || normalized === "PAUSING") return "warning";
  if (normalized === "PAUSED" || normalized === "PENDING") return "info";
  if (normalized === "FAILED") return "danger";
  return "info";
};

const sanitizeCheckerUserMessage = (value) => {
  const source = String(value || "");
  if (!source) {
    return "";
  }
  return source.replace(/^\s*(?:\[checker-runtime-v\d+\]\s*)+/i, "").trim();
};

const parseTaskLogMeta = (content) => {
  const text = String(content || "");
  const pick = (pattern) => {
    const matched = text.match(pattern);
    return matched?.[1]?.trim() || "";
  };
  return {
    benchmark: pick(/Benchmark:\s*(.+)$/im),
    repoUrl: pick(/Repo:\s*(.+)$/im),
    zipFileName: pick(/Zip:\s*(.+)$/im),
    llmModel:
      pick(/Model:\s*([^\n,]+)(?:,|$)/im) ||
      pick(/(?:LLM|Provider|Engine)\s*[:=]\s*([^\n,]+)(?:,|$)/im) ||
      pick(/(?:^|\s)--model\s+([^\s"'`]+)/i)
  };
};

const cleanupName = (value) => {
  const raw = String(value || "").trim();
  if (!raw) return "";
  return raw.replace(/^['"`]+|['"`]+$/g, "").trim();
};

const parseModelToken = (value) => {
  const text = cleanupName(value);
  if (!text) return "";
  const fromArg = text.match(/(?:^|\s)--model\s+([^\s"'`]+)/i)?.[1];
  if (fromArg) return cleanupName(fromArg);
  const fromNamed = text.match(/(?:^|\n)\s*(?:model|llm|provider|engine)\s*[:=]\s*([^\n,]+)(?:,|$)/im)?.[1];
  if (fromNamed) return cleanupName(fromNamed);
  return text;
};

const classifyModelKey = (value) => {
  const normalized = parseModelToken(value).toLowerCase();
  if (!normalized) return MODEL_FILTER_KEYS.OTHER;
  if (normalized.includes("deepseek")) return MODEL_FILTER_KEYS.DEEPSEEK;
  if (normalized.includes("hunyuan")) return MODEL_FILTER_KEYS.HUNYUAN;
  if (normalized.includes("kimi")) return MODEL_FILTER_KEYS.KIMI;
  return MODEL_FILTER_KEYS.OTHER;
};

const toModelDisplay = (rawModel) => {
  const raw = cleanupName(rawModel);
  if (!raw) return "-";
  const lower = raw.toLowerCase();
  if (lower.includes("kimi")) return "Kimi";
  if (lower.includes("deepseek")) return "DeepSeek";
  if (lower.includes("hunyuan")) return "Hunyuan";
  const compact = raw.replace(/\\/g, "/").split("/").filter(Boolean).pop() || raw;
  return compact || raw;
};

const buildModelMeta = (...candidates) => {
  for (const value of candidates) {
    const raw = parseModelToken(value);
    if (raw) {
      return {
        modelDisplay: toModelDisplay(raw),
        modelRaw: raw
      };
    }
  }
  return {
    modelDisplay: "-",
    modelRaw: ""
  };
};

const pickFileStem = (value) => {
  const raw = cleanupName(value);
  if (!raw) return "";
  const segment = raw.replace(/\\/g, "/").split("/").filter(Boolean).pop() || raw;
  return segment.replace(/\.git$/i, "").replace(/\.csv$/i, "");
};

const formatRepoDisplay = (value) => {
  const raw = cleanupName(value);
  if (!raw) return { display: "", raw: "" };
  try {
    const url = new URL(raw);
    const segments = url.pathname.split("/").filter(Boolean);
    const repo = (segments.pop() || "").replace(/\.git$/i, "");
    const owner = segments.pop() || "";
    if (owner && repo) {
      return { display: `${owner}/${repo}`, raw };
    }
    if (repo) {
      return { display: repo, raw };
    }
    return { display: url.hostname, raw };
  } catch (_) {
    const compact = raw.replace(/\\/g, "/").replace(/\/+$/g, "");
    const segments = compact.split("/").filter(Boolean);
    const repo = (segments.pop() || compact).replace(/\.git$/i, "");
    const owner = segments.pop() || "";
    if (owner && repo) {
      return { display: `${owner}/${repo}`, raw };
    }
    return { display: repo || compact, raw };
  }
};

const inferTypeCode = ({ taskType, datasetName, repoHint }) => {
  const normalized = String(taskType || "").toUpperCase();
  if (normalized.includes("CODE_REVIEW") || normalized.includes("REVIEW")) {
    return "CODE_REVIEW";
  }
  if (normalized.includes("NUTERA") || normalized.includes("LOCAL")) {
    return "NUTERA";
  }
  const hint = `${datasetName || ""} ${repoHint || ""}`.toLowerCase();
  if (hint.includes("github.com") || hint.includes(".git")) {
    return "CODE_REVIEW";
  }
  return "NUTERA";
};

const toTypeLabel = (typeCode) => (typeCode === "CODE_REVIEW" ? "工程代码" : "局部代码");

const shortTaskId = (taskId) => {
  const text = String(taskId || "").trim();
  if (!text) return "";
  return text.slice(0, 8);
};

const buildTaskName = ({ typeCode, datasetName, benchmark, repoUrl, zipFileName, taskId }) => {
  if (typeCode === "CODE_REVIEW") {
    const repo = formatRepoDisplay(repoUrl);
    if (repo.display) {
      return { display: repo.display, raw: repo.raw };
    }
    const zip = pickFileStem(zipFileName);
    if (zip) {
      return { display: zip, raw: cleanupName(zipFileName) };
    }
    return { display: shortTaskId(taskId) || "-", raw: "" };
  }

  const localCandidates = [datasetName, benchmark];
  for (const item of localCandidates) {
    const value = pickFileStem(item);
    if (value) {
      return { display: value, raw: cleanupName(item) };
    }
  }
  return { display: shortTaskId(taskId) || "-", raw: "" };
};

const parseTimeValue = (value) => {
  const date = new Date(value || "");
  return Number.isNaN(date.getTime()) ? 0 : date.getTime();
};

const mapBatchReportRow = (item) => {
  const datasetName = String(item?.datasetName ?? item?.dataset_name ?? "").trim();
  const modelMeta = buildModelMeta(item?.llmModel, item?.llm_model, item?.model, item?.llmConfig, item?.llm_config);
  const typeCode = inferTypeCode({
    taskType: item?.taskType ?? item?.task_type,
    datasetName,
    repoHint: ""
  });
  const taskName = buildTaskName({
    typeCode,
    datasetName,
    benchmark: "",
    repoUrl: datasetName,
    zipFileName: "",
    taskId: item?.taskId
  });
  return {
    ...item,
    sourceKind: "batch",
    supportsBatchOps: true,
    typeCode,
    typeLabel: toTypeLabel(typeCode),
    taskNameDisplay: taskName.display,
    taskNameRaw: taskName.raw,
    modelDisplay: modelMeta.modelDisplay,
    modelRaw: modelMeta.modelRaw,
    startedAt: item?.startedAt || "",
    finishedAt: item?.finishedAt || ""
  };
};

const mapTaskRow = (task, logMeta) => {
  const modelMeta = buildModelMeta(task?.llmModel, task?.llm_model, task?.model, task?.llmConfig, task?.llm_config, logMeta?.llmModel);
  const typeCode = inferTypeCode({
    taskType: task?.taskType,
    datasetName: "",
    repoHint: logMeta?.repoUrl
  });
  const taskName = buildTaskName({
    typeCode,
    datasetName: "",
    benchmark: logMeta?.benchmark,
    repoUrl: logMeta?.repoUrl,
    zipFileName: logMeta?.zipFileName,
    taskId: task?.taskId
  });
  const status = String(task?.status || "").toUpperCase();
  const isFinished = ["SUCCESS", "FAILED", "COMPLETED", "PAUSED"].includes(status);
  return {
    taskId: task?.taskId || "",
    sourceKind: "task",
    supportsBatchOps: false,
    typeCode,
    typeLabel: toTypeLabel(typeCode),
    taskNameDisplay: taskName.display,
    taskNameRaw: taskName.raw,
    modelDisplay: modelMeta.modelDisplay,
    modelRaw: modelMeta.modelRaw,
    status,
    startedAt: task?.createdAt || "",
    finishedAt: isFinished ? task?.updatedAt || "" : "",
    totalCases: 0,
    completedCases: 0,
    provedCount: 0,
    notProvedCount: 0,
    stopCount: 0,
    errorCount: status === "FAILED" ? 1 : 0,
    datasetName: "",
    message: sanitizeCheckerUserMessage(task?.message || "")
  };
};

const buildUnifiedRows = (batchRows, taskRows) => {
  const merged = new Map();
  for (const row of batchRows) {
    if (!row?.taskId) continue;
    merged.set(row.taskId, row);
  }
  for (const row of taskRows) {
    if (!row?.taskId) continue;
    if (!merged.has(row.taskId)) {
      merged.set(row.taskId, row);
      continue;
    }
    const base = merged.get(row.taskId);
    const shouldBackfillName = (!base?.taskNameDisplay || base.taskNameDisplay === "-") && row.taskNameDisplay;
    const shouldBackfillModel = (!base?.modelRaw && (base?.modelDisplay === "-" || !base?.modelDisplay)) && row.modelRaw;
    if (shouldBackfillName || shouldBackfillModel) {
      merged.set(row.taskId, {
        ...base,
        taskNameDisplay: shouldBackfillName ? row.taskNameDisplay : base.taskNameDisplay,
        taskNameRaw: shouldBackfillName ? row.taskNameRaw : base.taskNameRaw,
        modelDisplay: shouldBackfillModel ? row.modelDisplay : base.modelDisplay,
        modelRaw: shouldBackfillModel ? row.modelRaw : base.modelRaw
      });
    }
  }
  return Array.from(merged.values()).sort((a, b) => parseTimeValue(b.startedAt) - parseTimeValue(a.startedAt));
};

const ensureBatchOperationRow = (row) => {
  if (row?.supportsBatchOps) {
    return true;
  }
  ElMessage.info("该任务类型暂不支持批量报告操作");
  return false;
};

const playFilterEnterAnimation = () => {
  if (!filterBarRef.value) return;
  gsap.killTweensOf(filterBarRef.value);
  gsap.fromTo(
    filterBarRef.value,
    { autoAlpha: 0, y: -8 },
    { autoAlpha: 1, y: 0, duration: 0.32, ease: "power2.out" }
  );
};

const playTableSwitchAnimation = () => {
  if (!tableWrapRef.value) return;
  gsap.killTweensOf(tableWrapRef.value);
  gsap
    .timeline()
    .to(tableWrapRef.value, { autoAlpha: 0.68, duration: 0.08, ease: "power1.in" })
    .to(tableWrapRef.value, { autoAlpha: 1, duration: 0.16, ease: "power1.out" });
};

const scheduleTableSwitchAnimation = async () => {
  await nextTick();
  playTableSwitchAnimation();
};

const handlePageChange = (page) => {
  currentPage.value = Number(page || 1);
};

const loadReports = async () => {
  loading.value = true;
  try {
    const [batchSettled, taskSettled] = await Promise.allSettled([
      fetchBatchReports(200),
      fetchTaskList(200)
    ]);

    const batchRaw = batchSettled.status === "fulfilled" && Array.isArray(batchSettled.value)
      ? batchSettled.value
      : [];
    const taskRaw = taskSettled.status === "fulfilled" && Array.isArray(taskSettled.value?.tasks)
      ? taskSettled.value.tasks
      : [];

    if (batchSettled.status === "rejected" && taskSettled.status === "rejected") {
      throw new Error("加载报告与任务列表均失败");
    }
    if (batchSettled.status === "rejected") {
      ElMessage.warning(batchSettled.reason?.message || "批量报告加载失败，已回退到任务列表");
    }
    if (taskSettled.status === "rejected") {
      ElMessage.warning(taskSettled.reason?.message || "任务列表加载失败，仅显示批量报告");
    }

    const batchRows = batchRaw.map(mapBatchReportRow);

    const taskRowsSettled = await Promise.allSettled(
      taskRaw.map(async (task) => {
        try {
          const logResponse = await fetchTaskLogs(task.taskId);
          const logMeta = parseTaskLogMeta(logResponse?.content || "");
          return mapTaskRow(task, logMeta);
        } catch (_) {
          return mapTaskRow(task, {});
        }
      })
    );
    const taskRows = taskRowsSettled
      .filter((item) => item.status === "fulfilled")
      .map((item) => item.value);

    reports.value = buildUnifiedRows(batchRows, taskRows);
    currentPage.value = 1;
    await scheduleTableSwitchAnimation();
  } catch (error) {
    ElMessage.error(error.message || "加载报告列表失败");
    reports.value = [];
    currentPage.value = 1;
  } finally {
    loading.value = false;
  }
};

const openStats = async (row) => {
  if (!ensureBatchOperationRow(row)) return;
  const taskId = row?.taskId;
  if (!taskId) return;
  statsVisible.value = true;
  statsLoading.value = true;
  statsReport.value = null;
  try {
    statsReport.value = { ...(await fetchBatchReportDetail(taskId)), taskNameDisplay: row.taskNameDisplay };
  } catch (error) {
    ElMessage.error(error.message || "加载统计信息失败");
    statsVisible.value = false;
  } finally {
    statsLoading.value = false;
  }
};

const openCaseTable = async (row) => {
  if (!ensureBatchOperationRow(row)) return;
  const taskId = row?.taskId;
  if (!taskId) return;
  casesVisible.value = true;
  casesLoading.value = true;
  caseRows.value = [];
  caseReport.value = { ...row };
  try {
    const rows = await fetchBatchReportCases(taskId);
    caseRows.value = Array.isArray(rows)
      ? rows.map((item) => ({
          ...item,
          message: sanitizeCheckerUserMessage(item?.message)
        }))
      : [];
  } catch (error) {
    ElMessage.error(error.message || "加载 case 明细失败");
    casesVisible.value = false;
  } finally {
    casesLoading.value = false;
  }
};

const exportCases = async (row) => {
  if (!ensureBatchOperationRow(row)) return;
  const taskId = row?.taskId;
  if (!taskId) return;
  try {
    const { blob, filename } = await exportBatchReportCsv(taskId);
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = objectUrl;
    anchor.download = filename || "batch-report-cases.csv";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(objectUrl);
    ElMessage.success("CSV 导出成功");
  } catch (error) {
    ElMessage.error(error.message || "CSV 导出失败");
  }
};

const deleteReport = async (row) => {
  if (!ensureBatchOperationRow(row)) return;
  const taskId = row?.taskId;
  if (!taskId) return;
  try {
    await ElMessageBox.confirm(
      "删除后将同时移除该报告及其关联 case 明细，且无法恢复。是否继续？",
      "确认删除",
      {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消"
      }
    );
    await deleteBatchReport(taskId);
    reports.value = reports.value.filter((item) => item.taskId !== taskId);
    ElMessage.success("报告已删除");
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(error.message || "删除报告失败");
  }
};

watch([selectedType, selectedStatus, selectedModel, taskKeyword], async () => {
  if (currentPage.value !== 1) {
    currentPage.value = 1;
    return;
  }
  await scheduleTableSwitchAnimation();
});

watch(filteredRows, (rows) => {
  const maxPage = Math.max(1, Math.ceil(rows.length / pageSize));
  if (currentPage.value > maxPage) {
    currentPage.value = maxPage;
  }
});

watch(currentPage, async (nextPage, prevPage) => {
  if (nextPage === prevPage) return;
  await scheduleTableSwitchAnimation();
});

onMounted(async () => {
  await loadReports();
  await nextTick();
  playFilterEnterAnimation();
});

onBeforeUnmount(() => {
  if (filterBarRef.value) {
    gsap.killTweensOf(filterBarRef.value);
  }
  if (tableWrapRef.value) {
    gsap.killTweensOf(tableWrapRef.value);
  }
});
</script>

<style scoped>
.rp-card :deep(.el-table) {
  border-radius: 14px;
  overflow: hidden;
}

.rp-action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.rp-action-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.rp-filter-control {
  width: 128px;
}

.rp-model-filter-control {
  width: 146px;
}

.rp-search-control {
  width: 220px;
}

.rp-action-right :deep(.el-input__wrapper),
.rp-action-right :deep(.el-select__wrapper) {
  border-radius: 10px;
  border: 1px solid rgba(44, 107, 255, 0.2);
  box-shadow: none;
  background: #f8fbff;
}

.rp-action-right :deep(.el-input__wrapper.is-focus),
.rp-action-right :deep(.el-select__wrapper.is-focused) {
  border-color: rgba(44, 107, 255, 0.34);
}

.rp-table-wrap {
  min-height: 420px;
}

.rp-empty-wrap {
  padding: 14px 0;
}

.rp-type-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  border: 1px solid transparent;
}

.rp-type-pill.is-local {
  color: #1f4b82;
  background: #eaf2ff;
  border-color: #bbcff0;
}

.rp-type-pill.is-review {
  color: #0d5e32;
  background: #e5f6ed;
  border-color: #a8d8bc;
}

.rp-task-name-wrap {
  min-height: 38px;
  display: grid;
  align-content: center;
  gap: 2px;
}

.rp-task-name-main {
  color: #1f4b82;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.25;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rp-task-name-sub {
  color: #6d7f97;
  font-size: 11px;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rp-model-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  color: #606266;
  font-size: 13px;
  font-weight: 400;
  line-height: 1.4;
  letter-spacing: 0;
  text-align: center;
  vertical-align: middle;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rp-op-group {
  display: inline-flex;
  gap: 8px;
}

.rp-op-btn.el-button {
  width: 30px;
  height: 30px;
  border-color: rgba(44, 107, 255, 0.22);
  color: #2a5d9a;
  background: rgba(44, 107, 255, 0.08);
}

.rp-op-btn.el-button:hover {
  border-color: rgba(44, 107, 255, 0.34);
  background: rgba(44, 107, 255, 0.15);
}

.rp-op-btn.el-button.is-disabled {
  border-color: rgba(180, 191, 207, 0.4);
  color: #9ca9bc;
  background: rgba(210, 218, 230, 0.28);
}

.rp-op-btn.is-danger.el-button {
  border-color: rgba(230, 93, 105, 0.3);
  color: #c43d4f;
  background: rgba(235, 90, 104, 0.1);
}

.rp-op-btn.is-danger.el-button:hover {
  border-color: rgba(230, 93, 105, 0.48);
  background: rgba(235, 90, 104, 0.18);
}

.rp-op-icon {
  width: 14px;
  height: 14px;
}

.rp-pagination-wrap {
  margin-top: 12px;
  border: 1px solid rgba(44, 107, 255, 0.14);
  border-radius: 12px;
  background: #f8fbff;
  padding: 8px 10px;
  display: flex;
  justify-content: flex-end;
}

.rp-pagination-wrap :deep(.el-pagination) {
  gap: 6px;
}

.rp-pagination-wrap :deep(.el-pagination .btn-prev),
.rp-pagination-wrap :deep(.el-pagination .btn-next),
.rp-pagination-wrap :deep(.el-pagination.is-background .el-pager li) {
  border-radius: 8px;
  border: 1px solid rgba(44, 107, 255, 0.2);
  background: #ffffff;
  color: #2d5d96;
}

.rp-pagination-wrap :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: #eaf2ff;
  border-color: rgba(44, 107, 255, 0.34);
  color: #1f4b82;
}

.rp-pagination-wrap :deep(.el-pagination__total) {
  color: #5f7495;
}

.rp-loading-row {
  padding: 24px 0;
  text-align: center;
  color: #5f7296;
}

.rp-summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.rp-summary-item {
  display: grid;
  gap: 4px;
  border: 1px solid rgba(44, 107, 255, 0.16);
  border-radius: 14px;
  background: #f8fbff;
  padding: 10px 12px;
}

.rp-summary-item span {
  font-size: 12px;
  color: #607598;
}

.rp-summary-item strong {
  font-size: 14px;
  color: #1d3f66;
  font-weight: 600;
}

.rp-chart-panel {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
}

.rp-donut-box {
  display: grid;
  place-items: center;
}

.rp-donut {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  position: relative;
}

.rp-donut-hole {
  position: absolute;
  inset: 20px;
  border-radius: 50%;
  background: #ffffff;
  border: 1px solid rgba(44, 107, 255, 0.16);
  display: grid;
  place-items: center;
  align-content: center;
  gap: 2px;
}

.rp-donut-hole span {
  color: #1f4b82;
  font-size: 22px;
  font-weight: 700;
  line-height: 1;
}

.rp-donut-hole em {
  color: #6a7f9e;
  font-size: 11px;
  font-style: normal;
}

.rp-legend {
  display: grid;
  gap: 8px;
}

.rp-legend-item {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(44, 107, 255, 0.14);
  border-radius: 12px;
  background: #f8fbff;
  padding: 8px 10px;
}

.rp-legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.rp-legend-label {
  font-size: 12px;
  color: #335c8b;
}

.rp-legend-value {
  font-size: 13px;
  color: #1f4b82;
}

.rp-case-head {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-bottom: 12px;
  color: #3a5f8a;
  font-size: 13px;
}

@media (max-width: 1366px) {
  .rp-filter-control {
    width: 116px;
  }

  .rp-model-filter-control {
    width: 132px;
  }

  .rp-search-control {
    width: 190px;
  }
}

@media (max-width: 960px) {
  .rp-action-row {
    flex-direction: column;
    align-items: stretch;
  }

  .rp-action-right {
    justify-content: flex-start;
  }

  .rp-search-control {
    width: 100%;
  }

  .rp-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .rp-chart-panel {
    grid-template-columns: 1fr;
  }
}
</style>
