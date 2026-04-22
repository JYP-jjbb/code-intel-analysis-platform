<template>
  <el-card class="wb-card rp-card" shadow="never">
    <template #header>
      <div class="wb-card-head">
        <h3>报告中心</h3>
        <p>统一查看局部代码分析与工程代码审查任务</p>
      </div>
    </template>

    <div class="wb-action-row rp-action-row">
      <el-button type="primary" :loading="loading" @click="loadReports">刷新</el-button>
      <div ref="filterBarRef" class="rp-action-right">
        <el-select v-model="selectedType" class="rp-filter-control" size="small" placeholder="类型">
          <el-option v-for="item in typeFilterOptions" :key="item.value || 'all'" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="selectedStatus" class="rp-filter-control" size="small" placeholder="状态">
          <el-option v-for="item in statusFilterOptions" :key="item.value || 'all'" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="selectedModel" class="rp-filter-control rp-model-filter-control" size="small" placeholder="调用模型" filterable clearable>
          <el-option v-for="item in modelFilterOptions" :key="item.value || 'all'" :label="item.label" :value="item.value" />
        </el-select>
        <el-input v-model="taskKeyword" class="rp-search-control" size="small" clearable placeholder="搜索任务名称">
          <template #prefix><el-icon><Search /></el-icon></template>
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
            <span class="rp-type-pill" :class="row.typeCode === 'CODE_REVIEW' ? 'is-review' : 'is-local'">{{ row.typeLabel }}</span>
          </template>
        </el-table-column>
        <el-table-column label="任务名称" width="170" header-align="left" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="rp-task-name-wrap">
              <div class="rp-task-name-main" :title="row.taskNameRaw || row.taskNameDisplay">{{ row.taskNameDisplay }}</div>
              <div v-if="row.taskNameRaw && row.taskNameRaw !== row.taskNameDisplay" class="rp-task-name-sub" :title="row.taskNameRaw">{{ row.taskNameRaw }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="调用模型" min-width="210" align="center" show-overflow-tooltip>
          <template #default="{ row }"><span class="rp-model-cell" :title="row.modelRaw || row.modelDisplay">{{ row.modelDisplay || '-' }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="114" align="center">
          <template #default="{ row }"><el-tag size="small" :type="statusTagType(row.status)">{{ normalizeStatus(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="startedAtText" label="开始时间" min-width="168" align="center" />
        <el-table-column prop="finishedAtText" label="结束时间" min-width="168" align="center" />
        <el-table-column label="操作" min-width="220" align="center" fixed="right">
          <template #default="{ row }">
            <div class="rp-op-group">
              <el-tooltip content="统计预览" placement="top">
                <el-button class="rp-op-btn" circle @click.stop="openStats(row)">
                  <svg class="rp-op-icon" viewBox="0 0 24 24"><path d="M12 3a9 9 0 1 0 9 9h-9V3Zm2 0v7h7A9 9 0 0 0 14 3Z" fill="currentColor" /></svg>
                </el-button>
              </el-tooltip>
              <el-tooltip content="明细预览" placement="top">
                <el-button class="rp-op-btn" circle @click.stop="openDetail(row)">
                  <svg class="rp-op-icon" viewBox="0 0 24 24"><path d="M4 4h16a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Zm0 4v3h16V8H4Zm0 4v3h5v-3H4Zm7 0v3h9v-3h-9Z" fill="currentColor" /></svg>
                </el-button>
              </el-tooltip>
              <el-tooltip content="导出 CSV（仅局部代码）" placement="top">
                <el-button class="rp-op-btn" circle :disabled="row.typeCode !== 'NUTERA'" @click.stop="exportCases(row)">
                  <svg class="rp-op-icon" viewBox="0 0 24 24"><path d="M11 3h2v9.17l2.59-2.58L17 11l-5 5-5-5 1.41-1.41L11 12.17V3Zm-7 14h16v4H4v-4Z" fill="currentColor" /></svg>
                </el-button>
              </el-tooltip>
              <el-tooltip content="删除报告（仅局部代码）" placement="top">
                <el-button class="rp-op-btn is-danger" circle :disabled="row.typeCode !== 'NUTERA'" @click.stop="deleteReport(row)">
                  <svg class="rp-op-icon" viewBox="0 0 24 24"><path d="M8 4h8l1 2h4v2H3V6h4l1-2Zm-2 6h12l-1 10a1 1 0 0 1-1 .9H8a1 1 0 0 1-1-.9L6 10Zm3 2v6h2v-6H9Zm4 0v6h2v-6h-2Z" fill="currentColor" /></svg>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
        <template #empty><div class="rp-empty-wrap"><el-empty :description="tableEmptyDescription" /></div></template>
      </el-table>
    </div>

    <div class="rp-pagination-wrap">
      <el-pagination background layout="total, prev, pager, next" :total="filteredRows.length" :page-size="pageSize" :current-page="currentPage" @current-change="handlePageChange" />
    </div>
  </el-card>

  <el-dialog v-model="statsVisible" :title="statsType === 'CODE_REVIEW' ? '工程代码审查报告详情' : '局部代码任务统计'" width="88%" destroy-on-close>
    <div v-if="statsLoading" class="rp-loading-row">正在加载统计信息...</div>
    <template v-else-if="statsType === 'CODE_REVIEW' && codeReviewReport">
      <CodeReviewReportDashboard :report="codeReviewReport" :task-status="statsRow.status || '-'" />
    </template>
    <template v-else-if="statsType === 'NUTERA' && statsReport">
      <div class="rp-summary-grid">
        <div class="rp-summary-item"><span>任务名称</span><strong>{{ statsReport.taskNameDisplay || '-' }}</strong></div>
        <div class="rp-summary-item"><span>任务状态</span><strong>{{ normalizeStatus(statsReport.status) }}</strong></div>
        <div class="rp-summary-item"><span>总案例数</span><strong>{{ safeInt(statsReport.totalCases) }}</strong></div>
        <div class="rp-summary-item"><span>已完成</span><strong>{{ safeInt(statsReport.completedCases) }}</strong></div>
      </div>
      <div class="rp-chart-panel">
        <div class="rp-donut-box"><div class="rp-donut" :style="donutStyle"><div class="rp-donut-hole"><span>{{ completionRate }}%</span><em>完成率</em></div></div></div>
        <div class="rp-legend">
          <div v-for="item in statLegend" :key="item.key" class="rp-legend-item"><span class="rp-legend-dot" :style="{ backgroundColor: item.color }"></span><span class="rp-legend-label">{{ item.label }}</span><strong class="rp-legend-value">{{ item.value }}</strong></div>
        </div>
      </div>
    </template>
  </el-dialog>

  <el-dialog v-model="detailVisible" :title="detailTitle" width="88%" destroy-on-close>
    <div class="rp-case-head">
      <div><strong>任务名称:</strong> {{ detailRow.taskNameDisplay || '-' }}</div>
      <div><strong>状态:</strong> {{ normalizeStatus(detailRow.status) }}</div>
      <div><strong>类型:</strong> {{ detailRow.typeLabel || '-' }}</div>
    </div>
    <el-table v-loading="detailLoading" :data="detailRows" border stripe style="width: 100%">
      <el-table-column label="序号" width="68" align="center"><template #default="{ $index }">{{ $index + 1 }}</template></el-table-column>
      <el-table-column prop="name" label="名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="risk" label="风险等级" min-width="110" />
      <el-table-column prop="type" label="问题类型" min-width="150" show-overflow-tooltip />
      <el-table-column prop="location" label="文件/目录" min-width="260" show-overflow-tooltip />
      <el-table-column prop="suggestion" label="修复建议" min-width="300" show-overflow-tooltip />
    </el-table>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Search } from "@element-plus/icons-vue";
import gsap from "gsap";
import CodeReviewReportDashboard from "../components/CodeReviewReportDashboard.vue";
import { deleteBatchReport, exportBatchReportCsv, fetchBatchReportCases, fetchBatchReportDetail, fetchBatchReports, fetchTaskLogs } from "../api/nuteraApi.js";
import { fetchCodeReviewResult } from "../api/codeReviewApi.js";
import { fetchTaskList } from "../api/taskApi.js";

const loading = ref(false);
const reports = ref([]);
const filterBarRef = ref(null);
const tableWrapRef = ref(null);
const selectedType = ref("");
const selectedStatus = ref("");
const selectedModel = ref("");
const taskKeyword = ref("");
const currentPage = ref(1);
const pageSize = 10;

const statsVisible = ref(false);
const statsLoading = ref(false);
const statsType = ref("NUTERA");
const statsRow = ref({});
const statsReport = ref(null);
const codeReviewReport = ref(null);

const detailVisible = ref(false);
const detailLoading = ref(false);
const detailTitle = ref("任务明细");
const detailRow = ref({});
const detailRows = ref([]);

const typeFilterOptions = [{ label: "全部类型", value: "" }, { label: "局部代码", value: "NUTERA" }, { label: "工程代码", value: "CODE_REVIEW" }];
const MODEL_KEYS = { DEEPSEEK: "DEEPSEEK", HUNYUAN: "HUNYUAN", KIMI: "KIMI", QWEN: "QWEN", OTHER: "OTHER" };
const safeInt = (value) => Number.isFinite(Number(value)) ? Number(value) : 0;
const pad2 = (v) => String(v).padStart(2, "0");
const formatDateTime = (value) => {
  if (!value) return "-";
  const date = new Date(value);
  if (!Number.isNaN(date.getTime())) return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}:${pad2(date.getSeconds())}`;
  return String(value).replace("T", " ").replace("Z", "").replace(/\.\d+/, "").slice(0, 19);
};
const normalizeStatus = (value) => {
  const s = String(value || "").toUpperCase();
  if (!s) return "-";
  if (s === "SUCCESS") return "COMPLETED";
  return s;
};
const statusTagType = (value) => {
  const s = normalizeStatus(value);
  if (s === "COMPLETED") return "success";
  if (s === "RUNNING" || s === "PAUSING") return "warning";
  if (s === "FAILED") return "danger";
  return "info";
};
const parseTaskLogMeta = (content) => {
  const text = String(content || "");
  const pick = (pattern) => text.match(pattern)?.[1]?.trim() || "";
  return { benchmark: pick(/Benchmark:\s*(.+)$/im), repoUrl: pick(/Repo:\s*(.+)$/im), zipFileName: pick(/Zip:\s*(.+)$/im), llmModel: pick(/Model:\s*([^\n,]+)(?:,|$)/im) || pick(/(?:^|\s)--model\s+([^\s"'`]+)/i) };
};
const clean = (v) => String(v || "").trim().replace(/^['"`]+|['"`]+$/g, "");
const parseModelToken = (value) => {
  const text = clean(value);
  if (!text) return "";
  const fromArg = text.match(/(?:^|\s)--model\s+([^\s"'`]+)/i)?.[1];
  if (fromArg) return clean(fromArg);
  const fromNamed = text.match(/(?:^|\n)\s*(?:model|llm|provider|engine)\s*[:=]\s*([^\n,]+)(?:,|$)/im)?.[1];
  if (fromNamed) return clean(fromNamed);
  return text;
};
const classifyModelKey = (value) => {
  const model = parseModelToken(value).toLowerCase();
  if (!model) return MODEL_KEYS.OTHER;
  if (model.includes("deepseek")) return MODEL_KEYS.DEEPSEEK;
  if (model.includes("hunyuan")) return MODEL_KEYS.HUNYUAN;
  if (model.includes("qwen")) return MODEL_KEYS.QWEN;
  if (model.includes("moonshot") || model.includes("kimi")) return MODEL_KEYS.KIMI;
  return MODEL_KEYS.OTHER;
};
const toModelDisplay = (raw) => {
  const text = clean(raw);
  if (!text) return "-";
  const lower = text.toLowerCase();
  if (lower.includes("deepseek-reasoner")) return "DeepSeek Reasoner";
  if (lower.includes("deepseek-chat")) return "DeepSeek Chat";
  if (lower.includes("deepseek")) return "DeepSeek";
  if (lower.includes("hunyuan")) return "Hunyuan";
  if (lower.includes("qwen")) return "Qwen";
  if (lower.includes("moonshot") || lower.includes("kimi")) return "Kimi";
  return text.replace(/\\/g, "/").split("/").filter(Boolean).pop() || text;
};
const shortTaskId = (taskId) => String(taskId || "").slice(0, 8);
const formatRepo = (url) => {
  const raw = clean(url);
  if (!raw) return { display: "", raw: "" };
  try {
    const u = new URL(raw);
    const seg = u.pathname.split("/").filter(Boolean);
    const repo = (seg.pop() || "").replace(/\.git$/i, "");
    const owner = seg.pop() || "";
    return owner && repo ? { display: `${owner}/${repo}`, raw } : { display: repo || u.hostname, raw };
  } catch {
    const compact = raw.replace(/\\/g, "/").replace(/\/+$/g, "");
    const seg = compact.split("/").filter(Boolean);
    const repo = (seg.pop() || compact).replace(/\.git$/i, "");
    const owner = seg.pop() || "";
    return owner && repo ? { display: `${owner}/${repo}`, raw } : { display: repo || compact, raw };
  }
};
const inferTypeCode = ({ taskType, datasetName, repoHint }) => {
  const text = String(taskType || "").toUpperCase();
  if (text.includes("CODE_REVIEW") || text.includes("REVIEW")) return "CODE_REVIEW";
  if (text.includes("NUTERA") || text.includes("LOCAL")) return "NUTERA";
  const hint = `${datasetName || ""} ${repoHint || ""}`.toLowerCase();
  return hint.includes("github.com") || hint.includes(".git") ? "CODE_REVIEW" : "NUTERA";
};
const toTypeLabel = (typeCode) => typeCode === "CODE_REVIEW" ? "工程代码" : "局部代码";
const parseTime = (value) => { const d = new Date(value || ""); return Number.isNaN(d.getTime()) ? 0 : d.getTime(); };

const mapBatchReportRow = (item) => {
  const datasetName = String(item?.datasetName ?? item?.dataset_name ?? "").trim();
  const modelRaw = parseModelToken(item?.llmModel || item?.llm_model || item?.model || item?.llmConfig || item?.llm_config || "");
  const typeCode = inferTypeCode({ taskType: item?.taskType ?? item?.task_type, datasetName, repoHint: "" });
  const taskName = typeCode === "CODE_REVIEW" ? formatRepo(datasetName) : { display: datasetName.replace(/\.csv$/i, "") || shortTaskId(item?.taskId), raw: datasetName };
  return { ...item, sourceKind: "batch", taskId: item?.taskId || "", supportsBatchOps: true, typeCode, typeLabel: toTypeLabel(typeCode), taskNameDisplay: taskName.display || "-", taskNameRaw: taskName.raw || "", modelDisplay: toModelDisplay(modelRaw), modelRaw, startedAt: item?.startedAt || "", finishedAt: item?.finishedAt || "" };
};

const mapTaskRow = (task, meta) => {
  const typeCode = inferTypeCode({ taskType: task?.taskType, datasetName: "", repoHint: meta?.repoUrl });
  const modelRaw = parseModelToken(task?.llmModel || task?.llm_model || task?.model || task?.llmConfig || task?.llm_config || meta?.llmModel || "");
  const repo = formatRepo(meta?.repoUrl);
  const taskNameDisplay = typeCode === "CODE_REVIEW" ? (repo.display || shortTaskId(task?.taskId) || "-") : ((meta?.benchmark || "").replace(/\.csv$/i, "") || shortTaskId(task?.taskId) || "-");
  const status = String(task?.status || "").toUpperCase();
  const done = ["SUCCESS", "FAILED", "COMPLETED", "PAUSED"].includes(status);
  return { taskId: task?.taskId || "", sourceKind: "task", supportsBatchOps: false, typeCode, typeLabel: toTypeLabel(typeCode), taskNameDisplay, taskNameRaw: typeCode === "CODE_REVIEW" ? repo.raw : meta?.benchmark || "", modelDisplay: toModelDisplay(modelRaw), modelRaw, status, startedAt: task?.createdAt || "", finishedAt: done ? task?.updatedAt || "" : "" };
};

const buildUnifiedRows = (batchRows, taskRows) => {
  const merged = new Map();
  batchRows.forEach((row) => { if (row?.taskId) merged.set(row.taskId, row); });
  taskRows.forEach((row) => {
    if (!row?.taskId) return;
    if (!merged.has(row.taskId)) return void merged.set(row.taskId, row);
    const base = merged.get(row.taskId);
    merged.set(row.taskId, {
      ...base,
      typeCode: base.typeCode || row.typeCode,
      typeLabel: base.typeLabel || row.typeLabel,
      taskNameDisplay: base.taskNameDisplay && base.taskNameDisplay !== "-" ? base.taskNameDisplay : row.taskNameDisplay,
      taskNameRaw: base.taskNameRaw || row.taskNameRaw,
      modelDisplay: base.modelDisplay && base.modelDisplay !== "-" ? base.modelDisplay : row.modelDisplay,
      modelRaw: base.modelRaw || row.modelRaw
    });
  });
  return Array.from(merged.values()).sort((a, b) => parseTime(b.startedAt) - parseTime(a.startedAt));
};

const reportRows = computed(() => (Array.isArray(reports.value) ? reports.value : []).map((item) => ({ ...item, startedAtText: formatDateTime(item.startedAt), finishedAtText: formatDateTime(item.finishedAt) })));
const statusFilterOptions = computed(() => {
  const set = new Set(reportRows.value.map((row) => normalizeStatus(row.status)).filter((s) => s && s !== "-"));
  return [{ label: "全部状态", value: "" }, ...Array.from(set).sort().map((value) => ({ label: value, value }))];
});
const modelFilterOptions = computed(() => {
  const count = { [MODEL_KEYS.DEEPSEEK]: 0, [MODEL_KEYS.HUNYUAN]: 0, [MODEL_KEYS.KIMI]: 0, [MODEL_KEYS.QWEN]: 0, [MODEL_KEYS.OTHER]: 0 };
  reportRows.value.forEach((row) => { count[classifyModelKey(row.modelRaw || row.modelDisplay)] += 1; });
  return [
    { label: `全部模型 (${reportRows.value.length})`, value: "" },
    { label: `DeepSeek (${count[MODEL_KEYS.DEEPSEEK]})`, value: MODEL_KEYS.DEEPSEEK },
    { label: `Hunyuan (${count[MODEL_KEYS.HUNYUAN]})`, value: MODEL_KEYS.HUNYUAN },
    { label: `Kimi (${count[MODEL_KEYS.KIMI]})`, value: MODEL_KEYS.KIMI },
    { label: `Qwen (${count[MODEL_KEYS.QWEN]})`, value: MODEL_KEYS.QWEN },
    { label: `Other (${count[MODEL_KEYS.OTHER]})`, value: MODEL_KEYS.OTHER }
  ];
});
const filteredRows = computed(() => {
  const keyword = String(taskKeyword.value || "").trim().toLowerCase();
  return reportRows.value.filter((row) => {
    if (selectedType.value && row.typeCode !== selectedType.value) return false;
    if (selectedStatus.value && normalizeStatus(row.status) !== selectedStatus.value) return false;
    if (selectedModel.value && classifyModelKey(row.modelRaw || row.modelDisplay) !== selectedModel.value) return false;
    if (keyword) {
      const target = `${row.taskNameDisplay || ""} ${row.taskNameRaw || ""}`.toLowerCase();
      if (!target.includes(keyword)) return false;
    }
    return true;
  });
});
const pagedRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredRows.value.slice(start, start + pageSize).map((item, index) => ({ ...item, serialNo: start + index + 1 }));
});
const hasActiveFilter = computed(() => Boolean(selectedType.value || selectedStatus.value || selectedModel.value || String(taskKeyword.value || "").trim()));
const tableEmptyDescription = computed(() => hasActiveFilter.value ? "暂无匹配结果，请调整筛选条件" : "暂无报告数据");

const statLegend = computed(() => {
  const report = statsReport.value || {};
  const total = safeInt(report.totalCases);
  const proved = safeInt(report.provedCount);
  const notProved = safeInt(report.notProvedCount);
  const stop = safeInt(report.stopCount);
  const error = safeInt(report.errorCount);
  const pending = Math.max(total - (proved + notProved + stop + error), 0);
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
  if (!entries.length) return { background: "conic-gradient(#d7dfeb 0deg 360deg)" };
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
  const total = safeInt(statsReport.value?.totalCases);
  if (total <= 0) return 0;
  return Math.min(100, Math.round((safeInt(statsReport.value?.completedCases) / total) * 100));
});

const playFilterEnterAnimation = () => {
  if (!filterBarRef.value) return;
  gsap.killTweensOf(filterBarRef.value);
  gsap.fromTo(filterBarRef.value, { autoAlpha: 0, y: -8 }, { autoAlpha: 1, y: 0, duration: 0.32, ease: "power2.out" });
};
const playTableSwitchAnimation = () => {
  if (!tableWrapRef.value) return;
  gsap.killTweensOf(tableWrapRef.value);
  gsap.timeline().to(tableWrapRef.value, { autoAlpha: 0.72, duration: 0.1, ease: "power1.in" }).to(tableWrapRef.value, { autoAlpha: 1, duration: 0.16, ease: "power1.out" });
};
const scheduleTableSwitchAnimation = async () => { await nextTick(); playTableSwitchAnimation(); };
const handlePageChange = (page) => { currentPage.value = Number(page || 1); };

const loadReports = async () => {
  loading.value = true;
  try {
    const [batchSettled, taskSettled] = await Promise.allSettled([fetchBatchReports(200), fetchTaskList(200)]);
    const batchRaw = batchSettled.status === "fulfilled" && Array.isArray(batchSettled.value) ? batchSettled.value : [];
    const taskRaw = taskSettled.status === "fulfilled" && Array.isArray(taskSettled.value?.tasks) ? taskSettled.value.tasks : [];
    if (batchSettled.status === "rejected" && taskSettled.status === "rejected") throw new Error("加载报告与任务列表均失败");
    if (batchSettled.status === "rejected") ElMessage.warning(batchSettled.reason?.message || "批量报告加载失败，仅显示任务列表");
    if (taskSettled.status === "rejected") ElMessage.warning(taskSettled.reason?.message || "任务列表加载失败，仅显示批量报告");

    const batchRows = batchRaw.map(mapBatchReportRow);
    const taskRowsSettled = await Promise.allSettled(taskRaw.map(async (task) => {
      try {
        const logResponse = await fetchTaskLogs(task.taskId);
        return mapTaskRow(task, parseTaskLogMeta(logResponse?.content || ""));
      } catch {
        return mapTaskRow(task, {});
      }
    }));
    const taskRows = taskRowsSettled.filter((item) => item.status === "fulfilled").map((item) => item.value);
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
  if (!row?.taskId) return;
  statsVisible.value = true;
  statsLoading.value = true;
  statsRow.value = row;
  statsType.value = row.typeCode === "CODE_REVIEW" ? "CODE_REVIEW" : "NUTERA";
  statsReport.value = null;
  codeReviewReport.value = null;
  try {
    if (statsType.value === "CODE_REVIEW") {
      statsReport.value = { status: row.status, taskNameDisplay: row.taskNameDisplay };
      codeReviewReport.value = await fetchCodeReviewResult(row.taskId);
    } else {
      statsReport.value = { ...(await fetchBatchReportDetail(row.taskId)), taskNameDisplay: row.taskNameDisplay };
    }
  } catch (error) {
    ElMessage.error(error.message || "加载统计信息失败");
    statsVisible.value = false;
  } finally {
    statsLoading.value = false;
  }
};

const openDetail = async (row) => {
  if (!row?.taskId) return;
  detailVisible.value = true;
  detailLoading.value = true;
  detailRow.value = row;
  detailTitle.value = row.typeCode === "CODE_REVIEW" ? "工程代码问题明细" : "局部代码案例明细";
  detailRows.value = [];
  try {
    if (row.typeCode === "CODE_REVIEW") {
      const review = await fetchCodeReviewResult(row.taskId);
      const issues = Array.isArray(review?.reviewIssue) ? review.reviewIssue : [];
      detailRows.value = issues.map((item) => ({
        name: item?.title || "-",
        risk: item?.riskLevel || "-",
        type: item?.issueType || "-",
        location: [item?.directoryPath || "-", item?.filePath || "-", item?.lineStart ? `${item.lineStart}-${item.lineEnd}` : ""].filter(Boolean).join(" / "),
        suggestion: item?.suggestion || "-"
      }));
    } else {
      const rows = await fetchBatchReportCases(row.taskId);
      detailRows.value = (Array.isArray(rows) ? rows : []).map((item) => ({
        name: item?.caseName || "-",
        risk: item?.finalStatus || "-",
        type: item?.conclusion || "-",
        location: item?.candidateFunction || "-",
        suggestion: String(item?.message || "").replace(/^\s*(?:\[checker-runtime-v\d+\]\s*)+/i, "").trim()
      }));
    }
  } catch (error) {
    ElMessage.error(error.message || "加载任务明细失败");
    detailVisible.value = false;
  } finally {
    detailLoading.value = false;
  }
};

const exportCases = async (row) => {
  if (row?.typeCode !== "NUTERA") return;
  try {
    const { blob, filename } = await exportBatchReportCsv(row.taskId);
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
  if (row?.typeCode !== "NUTERA") return;
  try {
    await ElMessageBox.confirm("删除后将移除该报告及其关联明细，且无法恢复。是否继续？", "确认删除", {
      type: "warning",
      confirmButtonText: "删除",
      cancelButtonText: "取消"
    });
    await deleteBatchReport(row.taskId);
    reports.value = reports.value.filter((item) => item.taskId !== row.taskId);
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
  if (currentPage.value > maxPage) currentPage.value = maxPage;
});

watch(currentPage, async (next, prev) => {
  if (next === prev) return;
  await scheduleTableSwitchAnimation();
});

onMounted(async () => {
  await loadReports();
  await nextTick();
  playFilterEnterAnimation();
});

onBeforeUnmount(() => {
  if (filterBarRef.value) gsap.killTweensOf(filterBarRef.value);
  if (tableWrapRef.value) gsap.killTweensOf(tableWrapRef.value);
});
</script>

<style scoped>
.rp-card :deep(.el-table){border-radius:14px;overflow:hidden}
.rp-action-row{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}
.rp-action-right{display:flex;align-items:center;justify-content:flex-end;gap:8px;flex-wrap:wrap}
.rp-filter-control{width:128px}
.rp-model-filter-control{width:146px}
.rp-search-control{width:220px}
.rp-action-right :deep(.el-input__wrapper),.rp-action-right :deep(.el-select__wrapper){border-radius:10px;border:1px solid rgba(44,107,255,.2);box-shadow:none;background:#f8fbff}
.rp-action-right :deep(.el-input__wrapper.is-focus),.rp-action-right :deep(.el-select__wrapper.is-focused){border-color:rgba(44,107,255,.34)}
.rp-table-wrap{min-height:420px}
.rp-empty-wrap{padding:14px 0}
.rp-type-pill{display:inline-flex;align-items:center;justify-content:center;min-width:72px;padding:2px 8px;border-radius:999px;font-size:12px;font-weight:700;border:1px solid transparent}
.rp-type-pill.is-local{color:#1f4b82;background:#eaf2ff;border-color:#bbcff0}
.rp-type-pill.is-review{color:#0d5e32;background:#e5f6ed;border-color:#a8d8bc}
.rp-task-name-wrap{min-height:38px;display:grid;align-content:center;gap:2px}
.rp-task-name-main{color:#1f4b82;font-size:13px;font-weight:600;line-height:1.25;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.rp-task-name-sub{color:#6d7f97;font-size:11px;line-height:1.2;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.rp-model-cell{display:inline-flex;align-items:center;justify-content:center;width:100%;color:#606266;font-size:13px;line-height:1.4;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.rp-op-group{display:inline-flex;gap:8px}
.rp-op-btn.el-button{width:30px;height:30px;border-color:rgba(44,107,255,.22);color:#2a5d9a;background:rgba(44,107,255,.08)}
.rp-op-btn.el-button:hover{border-color:rgba(44,107,255,.34);background:rgba(44,107,255,.15)}
.rp-op-btn.el-button.is-disabled{border-color:rgba(180,191,207,.4);color:#9ca9bc;background:rgba(210,218,230,.28)}
.rp-op-btn.is-danger.el-button{border-color:rgba(230,93,105,.3);color:#c43d4f;background:rgba(235,90,104,.1)}
.rp-op-btn.is-danger.el-button:hover{border-color:rgba(230,93,105,.48);background:rgba(235,90,104,.18)}
.rp-op-icon{width:14px;height:14px}
.rp-pagination-wrap{margin-top:12px;border:1px solid rgba(44,107,255,.14);border-radius:12px;background:#f8fbff;padding:8px 10px;display:flex;justify-content:flex-end}
.rp-pagination-wrap :deep(.el-pagination .btn-prev),.rp-pagination-wrap :deep(.el-pagination .btn-next),.rp-pagination-wrap :deep(.el-pagination.is-background .el-pager li){border-radius:8px;border:1px solid rgba(44,107,255,.2);background:#fff;color:#2d5d96}
.rp-pagination-wrap :deep(.el-pagination.is-background .el-pager li.is-active){background:#eaf2ff;border-color:rgba(44,107,255,.34);color:#1f4b82}
.rp-loading-row{padding:24px 0;text-align:center;color:#5f7296}
.rp-summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin-bottom:16px}
.rp-summary-item{display:grid;gap:4px;border:1px solid rgba(44,107,255,.16);border-radius:14px;background:#f8fbff;padding:10px 12px}
.rp-summary-item span{font-size:12px;color:#607598}
.rp-summary-item strong{font-size:14px;color:#1d3f66;font-weight:600}
.rp-chart-panel{display:grid;grid-template-columns:220px minmax(0,1fr);align-items:center;gap:16px}
.rp-donut-box{display:grid;place-items:center}
.rp-donut{width:160px;height:160px;border-radius:50%;position:relative}
.rp-donut-hole{position:absolute;inset:20px;border-radius:50%;background:#fff;border:1px solid rgba(44,107,255,.16);display:grid;place-items:center;align-content:center;gap:2px}
.rp-donut-hole span{color:#1f4b82;font-size:22px;font-weight:700;line-height:1}
.rp-donut-hole em{color:#6a7f9e;font-size:11px;font-style:normal}
.rp-legend{display:grid;gap:8px}
.rp-legend-item{display:grid;grid-template-columns:10px minmax(0,1fr) auto;align-items:center;gap:8px;border:1px solid rgba(44,107,255,.14);border-radius:12px;background:#f8fbff;padding:8px 10px}
.rp-legend-dot{width:10px;height:10px;border-radius:50%}
.rp-legend-label{font-size:12px;color:#335c8b}
.rp-legend-value{font-size:13px;color:#1f4b82}
.rp-case-head{display:flex;flex-wrap:wrap;gap:18px;margin-bottom:12px;color:#3a5f8a;font-size:13px}
@media (max-width:1366px){.rp-filter-control{width:116px}.rp-model-filter-control{width:132px}.rp-search-control{width:190px}}
@media (max-width:960px){.rp-action-row{flex-direction:column;align-items:stretch}.rp-action-right{justify-content:flex-start}.rp-search-control{width:100%}.rp-summary-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.rp-chart-panel{grid-template-columns:1fr}}
</style>
