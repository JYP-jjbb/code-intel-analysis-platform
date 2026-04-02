<template>
  <div class="cr-dashboard">
    <div class="cr-summary-grid">
      <div class="cr-summary-item"><span>任务状态</span><strong>{{ taskStatusText }}</strong></div>
      <div class="cr-summary-item"><span>风险等级</span><strong>{{ riskLevelText }}</strong></div>
      <div class="cr-summary-item"><span>问题总数</span><strong>{{ issueCount }}</strong></div>
      <div class="cr-summary-item"><span>结果来源</span><strong>{{ sourceText }}</strong></div>
      <div class="cr-summary-item"><span>文件总数</span><strong>{{ coverage.totalFiles }}</strong></div>
      <div class="cr-summary-item"><span>审查覆盖率</span><strong>{{ coverage.coverageRate }}%</strong></div>
    </div>

    <div class="cr-chart-grid">
      <div class="cr-chart-card"><h4>风险等级分布</h4><div ref="riskRef" class="cr-chart"></div></div>
      <div class="cr-chart-card"><h4>问题类型分布</h4><div ref="typeRef" class="cr-chart"></div></div>
      <div class="cr-chart-card"><h4>目录风险热点</h4><div ref="dirRef" class="cr-chart"></div></div>
      <div class="cr-chart-card"><h4>高风险文件 Top N</h4><div ref="fileRef" class="cr-chart"></div></div>
      <div class="cr-chart-card"><h4>健康度雷达图</h4><div ref="radarRef" class="cr-chart"></div></div>
      <div class="cr-chart-card"><h4>健康度评分</h4><div ref="gaugeRef" class="cr-chart"></div></div>
      <div class="cr-chart-card"><h4>审查覆盖统计</h4><div ref="coverageRef" class="cr-chart"></div></div>
      <div class="cr-chart-card"><h4>语言/文件类型构成</h4><div ref="langRef" class="cr-chart"></div></div>
      <div class="cr-chart-card wide"><h4>修复收益评估</h4><div ref="benefitRef" class="cr-chart"></div></div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as echarts from "echarts";

const props = defineProps({
  report: { type: Object, default: () => ({}) },
  taskStatus: { type: String, default: "-" }
});

const riskRef = ref(null);
const typeRef = ref(null);
const dirRef = ref(null);
const fileRef = ref(null);
const radarRef = ref(null);
const gaugeRef = ref(null);
const coverageRef = ref(null);
const langRef = ref(null);
const benefitRef = ref(null);
const chartMap = new Map();

const RISK_KEYS = ["HIGH", "MEDIUM", "LOW", "INFO"];
const RISK_LABELS = { HIGH: "高风险", MEDIUM: "中风险", LOW: "低风险", INFO: "提示" };
const ISSUE_TYPES = [
  { key: "SECURITY", label: "安全性", pattern: /安全|security/i },
  { key: "ROBUSTNESS", label: "健壮性", pattern: /健壮|异常|null|robust/i },
  { key: "MAINTAINABILITY", label: "可维护性", pattern: /可维护|maintain|维护/i },
  { key: "PERFORMANCE", label: "性能", pattern: /性能|performance/i },
  { key: "STYLE", label: "代码规范", pattern: /规范|style|lint/i },
  { key: "TESTING", label: "测试缺失", pattern: /测试|test/i },
  { key: "ARCHITECTURE", label: "架构设计", pattern: /架构|design|architecture/i },
  { key: "DEPENDENCY", label: "依赖管理", pattern: /依赖|dependency/i }
];
const ISSUE_TYPE_KEY_ORDER = ISSUE_TYPES.map((item) => item.key);
const ISSUE_TYPE_LABEL_MAP = Object.fromEntries(ISSUE_TYPES.map((item) => [item.key, item.label]));

const issues = computed(() => (Array.isArray(props.report?.reviewIssue) ? props.report.reviewIssue : []));
const issueCount = computed(() => Number(props.report?.reviewSummary?.issueCount ?? issues.value.length ?? 0));
const sourceText = computed(() => (props.report?.fallbackResult ? "兜底结果" : "真实模型"));
const coverage = computed(() => ({
  totalFiles: Number(props.report?.coverageStats?.totalFiles || 0),
  analyzedFiles: Number(props.report?.coverageStats?.analyzedFiles || 0),
  skippedFiles: Number(props.report?.coverageStats?.skippedFiles || 0),
  coverageRate: Number(props.report?.coverageStats?.coverageRate || 0)
}));

const normalizeRiskKey = (value) => {
  const raw = String(value || "").toUpperCase();
  if (raw.includes("HIGH") || raw.includes("高")) return "HIGH";
  if (raw.includes("MEDIUM") || raw.includes("中")) return "MEDIUM";
  if (raw.includes("LOW") || raw.includes("低")) return "LOW";
  return "INFO";
};
const riskLevelText = computed(() => RISK_LABELS[normalizeRiskKey(props.report?.reviewSummary?.riskLevel || props.report?.riskLevel || "INFO")] || RISK_LABELS.INFO);

const normalizeStatusText = (value) => {
  const raw = String(value || "").toUpperCase();
  if (!raw) return "-";
  if (raw === "SUCCESS" || raw === "COMPLETED") return "已完成";
  if (raw === "RUNNING") return "运行中";
  if (raw === "FAILED") return "失败";
  if (raw === "PAUSED") return "已暂停";
  if (raw === "PENDING") return "待执行";
  return value;
};
const taskStatusText = computed(() => normalizeStatusText(props.taskStatus));

const normalizeIssueType = (value) => {
  const raw = String(value || "");
  const matched = ISSUE_TYPES.find((item) => item.pattern.test(raw));
  return matched ? matched.key : "MAINTAINABILITY";
};
const toIssueTypeLabel = (value) => ISSUE_TYPE_LABEL_MAP[normalizeIssueType(value)] || ISSUE_TYPE_LABEL_MAP.MAINTAINABILITY;

const benefitColor = (level) => {
  const value = String(level || "").toUpperCase();
  if (value.includes("HIGH")) return "#eb5a68";
  if (value.includes("MEDIUM")) return "#f2bf43";
  return "#5a9af8";
};

const getChart = (key, refEl) => {
  if (!refEl.value) return null;
  if (chartMap.has(key)) return chartMap.get(key);
  const instance = echarts.init(refEl.value);
  chartMap.set(key, instance);
  return instance;
};

const drawRisk = () => {
  const counter = { HIGH: 0, MEDIUM: 0, LOW: 0, INFO: 0 };
  issues.value.forEach((item) => {
    counter[normalizeRiskKey(item?.riskLevel)] += 1;
  });
  const chart = getChart("risk", riskRef);
  if (!chart) return;
  chart.setOption({
    tooltip: {
      trigger: "item",
      formatter: (params) => `${params.name}<br/>数量：${params.value}<br/>占比：${params.percent}%`
    },
    color: ["#eb5a68", "#f2bf43", "#4fa95d", "#6c86a8"],
    legend: { bottom: 0, textStyle: { color: "#486488", fontSize: 11 } },
    series: [{
      name: "风险分布",
      type: "pie",
      radius: ["46%", "72%"],
      data: RISK_KEYS.map((key) => ({ name: RISK_LABELS[key], value: counter[key] }))
    }]
  });
};

const drawType = () => {
  const counter = new Map(ISSUE_TYPE_KEY_ORDER.map((key) => [key, 0]));
  issues.value.forEach((item) => {
    const key = normalizeIssueType(item?.issueType);
    counter.set(key, (counter.get(key) || 0) + 1);
  });
  const labels = ISSUE_TYPE_KEY_ORDER.map((key) => ISSUE_TYPE_LABEL_MAP[key]);
  const chart = getChart("type", typeRef);
  if (!chart) return;
  chart.setOption({
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params) => {
        const row = Array.isArray(params) ? params[0] : params;
        return `${row?.name || "-"}<br/>问题数：${row?.value ?? 0}`;
      }
    },
    grid: { top: 20, left: 84, right: 20, bottom: 24 },
    xAxis: { type: "value", name: "问题数", nameTextStyle: { color: "#5f7596" } },
    yAxis: { type: "category", inverse: true, data: labels, axisLabel: { fontSize: 11, color: "#486488" } },
    series: [{
      name: "问题数",
      type: "bar",
      barWidth: 14,
      data: ISSUE_TYPE_KEY_ORDER.map((key) => counter.get(key) || 0),
      itemStyle: { color: "#6da4ff", borderRadius: [0, 8, 8, 0] }
    }]
  });
};

const drawDirectory = () => {
  const rows = Array.isArray(props.report?.directoryStats) ? props.report.directoryStats.slice(0, 10) : [];
  const chart = getChart("dir", dirRef);
  if (!chart) return;
  chart.setOption({
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params) => {
        const row = Array.isArray(params) ? params[0] : params;
        return `目录：${row?.name || "-"}<br/>问题数：${row?.value ?? 0}`;
      }
    },
    grid: { top: 20, left: 48, right: 20, bottom: 34 },
    xAxis: {
      type: "category",
      data: rows.map((r) => r.directoryPath || "/"),
      axisLabel: { rotate: 24, fontSize: 10, color: "#486488" }
    },
    yAxis: { type: "value", name: "问题数", nameTextStyle: { color: "#5f7596" } },
    series: [{
      name: "问题数",
      type: "bar",
      data: rows.map((r) => Number(r.issueCount || 0)),
      itemStyle: { color: "#8cb8ff", borderRadius: [8, 8, 0, 0] }
    }]
  });
};

const drawFile = () => {
  const rows = Array.isArray(props.report?.fileRiskStats) ? props.report.fileRiskStats.slice(0, 8).reverse() : [];
  const chart = getChart("file", fileRef);
  if (!chart) return;
  chart.setOption({
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params) => {
        const row = Array.isArray(params) ? params[0] : params;
        return `文件：${row?.name || "-"}<br/>风险分：${row?.value ?? 0}`;
      }
    },
    grid: { top: 20, left: 126, right: 20, bottom: 24 },
    xAxis: { type: "value", name: "风险分", nameTextStyle: { color: "#5f7596" } },
    yAxis: {
      type: "category",
      data: rows.map((r) => String(r.filePath || "-").split("/").pop()),
      axisLabel: { fontSize: 10, color: "#486488" }
    },
    series: [{
      name: "风险分",
      type: "bar",
      data: rows.map((r) => Number(r.riskScore || 0)),
      itemStyle: { color: "#f29d4b", borderRadius: [0, 8, 8, 0] }
    }]
  });
};

const drawRadar = () => {
  const h = props.report?.healthScore || {};
  const chart = getChart("radar", radarRef);
  if (!chart) return;
  chart.setOption({
    tooltip: {
      trigger: "item",
      formatter: (params) => {
        const items = (params?.value || []).map((v, i) => `${params?.dimensionNames?.[i] || ""}：${v}`);
        return `健康度维度<br/>${items.join("<br/>")}`;
      }
    },
    radar: {
      radius: "60%",
      indicator: [
        { name: "安全性", max: 100 },
        { name: "健壮性", max: 100 },
        { name: "可维护性", max: 100 },
        { name: "测试完备度", max: 100 },
        { name: "结构清晰度", max: 100 }
      ]
    },
    series: [{
      name: "健康度",
      type: "radar",
      data: [{
        value: [
          Number(h.security || 0),
          Number(h.robustness || 0),
          Number(h.maintainability || 0),
          Number(h.testingCompleteness || 0),
          Number(h.structureClarity || 0)
        ],
        areaStyle: { color: "rgba(109,164,255,0.24)" },
        lineStyle: { color: "#4d82d8" }
      }]
    }]
  });
};

const drawGauge = () => {
  const overall = Number(props.report?.healthScore?.overall || 0);
  const chart = getChart("gauge", gaugeRef);
  if (!chart) return;
  chart.setOption({
    series: [{
      type: "gauge",
      min: 0,
      max: 100,
      splitNumber: 5,
      axisLine: { lineStyle: { width: 14, color: [[0.4, "#eb5a68"], [0.7, "#f2bf43"], [1, "#4fa95d"]] } },
      detail: { formatter: "{value} 分", fontSize: 20, color: "#2a5d9a" },
      data: [{ value: Math.max(0, Math.min(100, overall)), name: "健康评分" }]
    }]
  });
};

const drawCoverage = () => {
  const c = coverage.value;
  const chart = getChart("coverage", coverageRef);
  if (!chart) return;
  chart.setOption({
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params) => {
        const rows = Array.isArray(params) ? params : [params];
        return rows.map((row) => `${row?.name || "-"}：${row?.value ?? 0}`).join("<br/>");
      }
    },
    grid: { top: 20, left: 40, right: 20, bottom: 24 },
    xAxis: { type: "category", data: ["文件总数", "已分析", "已跳过"], axisLabel: { color: "#486488" } },
    yAxis: { type: "value", name: "文件数", nameTextStyle: { color: "#5f7596" } },
    series: [{
      name: "文件数",
      type: "bar",
      data: [c.totalFiles, c.analyzedFiles, c.skippedFiles],
      itemStyle: { color: "#7cb6ff", borderRadius: [8, 8, 0, 0] }
    }],
    graphic: {
      type: "text",
      left: "center",
      top: 6,
      style: { text: `审查覆盖率 ${c.coverageRate}%`, fill: "#2a5d9a", fontSize: 12, fontWeight: 600 }
    }
  });
};

const normalizeLanguageRows = (rows) => {
  const parsed = (Array.isArray(rows) ? rows : [])
    .map((item) => ({
      name: String(item?.language || "其他").trim() || "其他",
      value: Number(item?.fileCount || 0)
    }))
    .filter((item) => item.value > 0)
    .sort((a, b) => b.value - a.value);
  if (!parsed.length) return [];
  const total = parsed.reduce((sum, item) => sum + item.value, 0);
  const minKeepThreshold = total * 0.03;
  let other = 0;
  const kept = [];
  parsed.forEach((item, index) => {
    const isSmall = item.value < minKeepThreshold;
    const overflow = index >= 7;
    if ((isSmall || overflow) && parsed.length > 4) other += item.value;
    else kept.push(item);
  });
  if (other > 0) kept.push({ name: "其他", value: other });
  return kept;
};

const drawLanguage = () => {
  const dataRows = normalizeLanguageRows(props.report?.languageStats);
  const showOuterLabel = dataRows.length <= 6;
  const chart = getChart("lang", langRef);
  if (!chart) return;
  chart.setOption({
    tooltip: {
      trigger: "item",
      formatter: (params) => `${params.name}<br/>文件数：${params.value}<br/>占比：${params.percent}%`
    },
    legend: {
      type: "scroll",
      bottom: 2,
      left: "center",
      itemWidth: 10,
      itemHeight: 10,
      itemGap: 10,
      textStyle: { color: "#486488", fontSize: 11 }
    },
    series: [{
      name: "语言构成",
      type: "pie",
      center: ["50%", "40%"],
      radius: ["38%", "62%"],
      avoidLabelOverlap: true,
      minAngle: 3,
      label: showOuterLabel ? { show: true, fontSize: 11, color: "#426089", formatter: "{b} {d}%" } : { show: false },
      labelLine: showOuterLabel ? { show: true, length: 8, length2: 8, maxSurfaceAngle: 80 } : { show: false },
      data: dataRows.length ? dataRows : [{ name: "暂无数据", value: 1, itemStyle: { color: "#d7e2f5" } }]
    }]
  });
};

const drawBenefit = () => {
  const rows = Array.isArray(props.report?.repairBenefitStats) ? props.report.repairBenefitStats : [];
  const chart = getChart("benefit", benefitRef);
  if (!chart) return;
  chart.setOption({
    tooltip: {
      trigger: "item",
      formatter: (params) => {
        const [risk, cost, score] = params.data.value;
        return `对象：${params.data.name}<br/>风险分：${risk}<br/>修复成本：${cost}<br/>收益分：${score}`;
      }
    },
    grid: { top: 20, left: 58, right: 20, bottom: 34 },
    xAxis: { name: "风险分", type: "value", nameTextStyle: { color: "#5f7596" } },
    yAxis: { name: "修复成本", type: "value", nameTextStyle: { color: "#5f7596" } },
    series: [{
      name: "收益点",
      type: "scatter",
      symbolSize: (val) => Math.max(10, Math.min(34, Number(val?.[2] || 0) * 8)),
      data: rows.map((item) => ({
        name: String(item.filePath || "-").split("/").pop(),
        value: [Number(item.riskScore || 0), Number(item.repairCost || 0), Number(item.benefitScore || 0)],
        itemStyle: { color: benefitColor(item.benefitLevel) }
      }))
    }]
  });
};

const renderAll = async () => {
  await nextTick();
  drawRisk();
  drawType();
  drawDirectory();
  drawFile();
  drawRadar();
  drawGauge();
  drawCoverage();
  drawLanguage();
  drawBenefit();
};

const resizeAll = () => chartMap.forEach((chart) => chart.resize());

watch(() => props.report, () => { void renderAll(); }, { deep: true });

onMounted(() => {
  void renderAll();
  window.addEventListener("resize", resizeAll);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", resizeAll);
  chartMap.forEach((chart) => chart.dispose());
  chartMap.clear();
});
</script>

<style scoped>
.cr-dashboard { display: grid; gap: 12px; }
.cr-summary-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 8px; }
.cr-summary-item { border: 1px solid rgba(44, 107, 255, 0.15); border-radius: 12px; background: #f8fbff; padding: 8px 10px; display: grid; gap: 4px; }
.cr-summary-item span { font-size: 12px; color: #607598; }
.cr-summary-item strong { font-size: 13px; color: #1d3f66; line-height: 1.4; }
.cr-chart-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.cr-chart-card { border: 1px solid rgba(44, 107, 255, 0.15); border-radius: 14px; background: #f9fcff; padding: 10px; display: grid; gap: 8px; }
.cr-chart-card h4 { margin: 0; font-size: 13px; color: #204d83; }
.cr-chart-card.wide { grid-column: span 2; }
.cr-chart { height: 260px; }
@media (max-width: 1280px) { .cr-summary-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 960px) {
  .cr-summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .cr-chart-grid { grid-template-columns: 1fr; }
  .cr-chart-card.wide { grid-column: span 1; }
}
</style>
