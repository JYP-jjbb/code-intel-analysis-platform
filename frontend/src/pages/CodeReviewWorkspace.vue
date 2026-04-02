<template>
  <div class="nutera-workbench code-review-workbench">
    <div class="nutera-grid code-review-grid">
      <section class="nutera-operation code-review-operation">
        <el-card class="wb-card wb-task-config-card cr-task-card" shadow="never">
          <template #header>
            <div class="wb-card-head"><h3>审查任务配置</h3></div>
          </template>

          <div class="wb-config-toolbar cr-config-toolbar">
            <div class="wb-config-chip cr-upload-chip">
              <span class="wb-config-chip-label">文件上传</span>
              <el-upload :auto-upload="false" :show-file-list="false" :on-change="handleFileChange">
                <el-button class="wb-chip-upload-btn" size="small"><el-icon><UploadFilled /></el-icon><span>选择文件</span></el-button>
              </el-upload>
            </div>

            <div class="wb-config-chip is-repo-chip cr-repo-chip">
              <span class="wb-config-chip-label">代码仓库</span>
              <el-input v-model="form.repoUrl" class="wb-chip-control cr-repo-url-control" size="small" placeholder="https://github.com/owner/repo" />
              <el-button class="cr-repo-download" size="small" :loading="downloadingRepo" @click="downloadRepository">下载</el-button>
            </div>

            <div class="wb-config-chip cr-model-chip">
              <span class="wb-config-chip-label">调用模型</span>
              <el-select v-model="form.model" class="wb-chip-control" size="small" placeholder="选择模型">
                <el-option v-for="item in modelOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </div>
          </div>

          <div class="wb-form-group wb-code-group">
            <label class="wb-field-label">代码展示区 / 项目入口代码展示区</label>
            <div class="wb-code-editor cr-code-editor">
              <pre ref="gutterRef" class="wb-code-gutter" aria-hidden="true">{{ lineNumbers }}</pre>
              <textarea v-model="form.codePreview" class="wb-code-textarea" spellcheck="false" readonly @scroll="syncGutterScroll" />
            </div>
            <div class="cr-code-caption">当前焦点文件：{{ form.focusFilePath || "-" }}</div>
          </div>

          <div class="wb-action-row wb-main-actions">
            <el-button class="wb-primary-btn" type="primary" :loading="submitting" @click="submitTask">
              <el-icon><Check /></el-icon>
              {{ task.status === "RUNNING" ? "审查中..." : "开始审查" }}
            </el-button>
            <el-button class="wb-soft-btn" @click="resetForm"><el-icon><Brush /></el-icon>清空</el-button>
          </div>
        </el-card>
      </section>

      <section class="nutera-display code-review-display">
        <el-card class="wb-card wb-log-card cr-tree-card" shadow="never">
          <template #header>
            <div class="wb-card-head"><h3>项目树</h3><p>{{ selectedTreePath || "等待项目结构输出" }}</p></div>
          </template>
          <div class="cr-tree-scroll">
            <el-tree
              v-if="treeData.length"
              :data="treeData"
              node-key="id"
              :props="treeProps"
              :expand-on-click-node="false"
              :highlight-current="true"
              :default-expanded-keys="expandedTreeKeys"
              :current-node-key="selectedTreeNodeKey"
              @node-click="handleTreeNodeClick"
            />
            <el-empty v-else description="暂无项目结构数据" />
          </div>
        </el-card>
      </section>

      <section class="code-review-output">
        <el-card ref="outputCardRef" class="wb-card wb-summary-card cr-output-card" shadow="never">
          <template #header>
            <div class="wb-card-head">
              <h3>审查分析输出</h3>
              <p>来源：{{ result.resultSource || "-" }}</p>
            </div>
          </template>

          <div class="wb-block">
            <div class="wb-block-title">审查进度</div>
            <div class="cr-stage-banner" :class="`is-${outputBannerType}`">
              <el-icon v-if="isOutputRunning" class="is-loading"><Loading /></el-icon>
              <span>{{ outputStatusText }}</span>
            </div>
            <div class="cr-stage-flow">
              <div v-for="(stage, idx) in reviewStages" :key="stage.key" class="cr-stage-item" :class="`is-${stageState(idx)}`">
                <span class="cr-stage-index">{{ idx + 1 }}</span>
                <span class="cr-stage-name">{{ stage.label }}</span>
              </div>
            </div>
          </div>

          <div class="wb-block">
            <div class="wb-block-title">任务概览</div>
            <div class="cr-overview-grid">
              <div class="cr-overview-item"><span>任务状态</span><el-tag size="small" :type="statusTagType">{{ taskStatusText }}</el-tag></div>
              <div class="cr-overview-item"><span>风险等级</span><el-tag size="small" :type="riskTagType">{{ riskLevelText }}</el-tag></div>
              <div class="cr-overview-item"><span>问题总数</span><strong>{{ issueRows.length }}</strong></div>
              <div class="cr-overview-item"><span>模型配置</span><strong>{{ result.reviewTask?.model || form.model }}</strong></div>
              <div class="cr-overview-item cr-progress-item">
                <span>当前进度</span>
                <el-progress :percentage="taskProgress" :stroke-width="10" />
              </div>
              <div class="cr-overview-item">
                <span>结果类型</span>
                <el-tag size="small" :type="result.fallbackResult ? 'warning' : 'success'">{{ result.fallbackResult ? "兜底结果" : "真实模型" }}</el-tag>
              </div>
            </div>
            <el-alert
              v-if="result.fallbackResult"
              class="cr-fallback-alert"
              type="warning"
              :closable="false"
              show-icon
              title="当前为兜底结果（非真实 LLM 审查）"
              :description="result.fallbackReason || '模型调用失败或结果解析失败。'"
            />
          </div>

          <div class="wb-block">
            <div class="wb-block-title">工程信息</div>
            <div class="cr-meta-grid">
              <div><span>仓库地址</span><strong>{{ result.reviewTask?.repoUrl || form.repoUrl || "-" }}</strong></div>
              <div><span>项目目录</span><strong>{{ result.reviewTask?.projectPath || form.localFolder || "-" }}</strong></div>
              <div><span>总文件数</span><strong>{{ coverageStats.totalFiles }}</strong></div>
              <div><span>实际分析</span><strong>{{ coverageStats.analyzedFiles }}</strong></div>
              <div><span>跳过文件</span><strong>{{ coverageStats.skippedFiles }}</strong></div>
              <div><span>覆盖率</span><strong>{{ coverageStats.coverageRate }}%</strong></div>
            </div>
            <div class="cr-lang-tags" v-if="languageTop.length">
              <el-tag v-for="lang in languageTop" :key="lang.language" size="small" effect="plain">{{ lang.language }} {{ lang.fileCount }} ({{ lang.percentage }}%)</el-tag>
            </div>
          </div>

          <div class="wb-block">
            <div class="wb-block-title">问题列表</div>
            <div v-if="issueRows.length" class="cr-issue-list">
              <article v-for="(item, index) in issueRows" :key="`issue-${index}`" class="cr-issue-item">
                <header>
                  <span>{{ index + 1 }}. {{ item.title }}</span>
                  <el-tag size="small" :type="riskTagTypeBy(item.riskLevel)">{{ item.riskLevel }}</el-tag>
                </header>
                <p>{{ item.description }}</p>
                <div class="cr-issue-meta">
                  <span>类型：{{ item.issueType }}</span>
                  <span>目录：{{ item.directoryPath || "-" }}</span>
                  <span>文件：{{ item.filePath || "-" }}</span>
                  <span>行号：{{ item.lineStart > 0 ? `${item.lineStart}-${item.lineEnd}` : "-" }}</span>
                  <span>来源：{{ item.fromRealLlm ? "真实 LLM" : "兜底规则" }}</span>
                </div>
                <p><strong>修复建议：</strong>{{ item.suggestion || "暂无" }}</p>
              </article>
            </div>
            <div v-else class="cr-empty">等待问题清单输出</div>
          </div>

          <div class="wb-block">
            <div class="wb-block-title">修复建议</div>
            <ul v-if="suggestionItems.length" class="cr-list">
              <li v-for="(item, index) in suggestionItems" :key="`suggestion-${index}`">{{ item }}</li>
            </ul>
            <div v-else class="cr-empty">等待修复建议输出</div>
          </div>

          <div class="wb-block">
            <div class="wb-block-title">总体结论</div>
            <pre class="wb-code-block">{{ summaryText || "等待总体审查结论..." }}</pre>
          </div>

          <div class="wb-block">
            <div class="wb-block-title">阶段时间线</div>
            <ul v-if="timelineRows.length" class="cr-timeline-list">
              <li v-for="(item, index) in timelineRows" :key="`tl-${index}`">
                <span>{{ formatTs(item.timestamp) }}</span>
                <strong>{{ item.stage }}</strong>
                <el-tag size="small" :type="timelineTagType(item.status)">{{ item.status }}</el-tag>
                <span>{{ item.progress }}%</span>
                <span>{{ item.message }}</span>
              </li>
            </ul>
            <div v-else class="cr-empty">等待阶段日志...</div>
          </div>

          <div class="wb-block">
            <div class="wb-block-title">执行日志</div>
            <pre class="wb-code-block cr-log-preview">{{ logs || "等待执行日志..." }}</pre>
          </div>
        </el-card>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Brush, Check, Loading, UploadFilled } from "@element-plus/icons-vue";
import {
  API_BASE,
  cleanupCodeReviewProject,
  downloadCodeReviewRepository,
  fetchCodeReviewProjectFile,
  fetchCodeReviewResult,
  fetchTaskDetail,
  fetchTaskLogs,
  submitCodeReviewTask
} from "../api/codeReviewApi.js";
import {
  clearCodeReviewProjectSnapshot,
  generatePageSessionId,
  loadCodeReviewProjectSnapshot,
  saveCodeReviewProjectSnapshot
} from "../stores/codeReviewProjectSessionStore.js";

const DEFAULT_CODE_PREVIEW = `// 请上传源码文件或填写 GitHub 仓库地址，然后点击“开始审查”。
// 本区域显示当前选中文件源码；右侧展示后端真实审查结果和执行日志。`;
const treeProps = { label: "label", children: "children" };
const modelOptions = [
  { label: "Kimi", value: "kimi-k2.5" },
  { label: "DeepSeek", value: "deepseek-ai/DeepSeek-V3.2" },
  { label: "Hunyuan", value: "hunyuan-2.0-thinking-20251109" },
  { label: "Qwen", value: "qwen3.5-plus" }
];

const form = reactive({ repoUrl: "", zipFileName: "", localFolder: "", model: "kimi-k2.5", codePreview: DEFAULT_CODE_PREVIEW, focusFilePath: "" });
const task = reactive({ taskId: "", status: "", createdAt: "", updatedAt: "", message: "" });
const logs = ref("");
const result = reactive({
  projectStructure: "", issueList: "", riskLevel: "", fixSuggestions: "", summary: "",
  fallbackResult: false, fallbackReason: "", resultSource: "", reviewTask: null, reviewSummary: null,
  reviewIssue: [], coverageStats: null, languageStats: [], timelineLogs: []
});
const gutterRef = ref(null);
const submitting = ref(false);
const downloadingRepo = ref(false);
const treeData = ref([]);
const expandedTreeKeys = ref([]);
const selectedTreeNodeKey = ref("");
const selectedTreePath = ref("");
const outputCardRef = ref(null);
const previewMode = ref("default");
const treePathMap = ref({});
const pageSessionId = ref(generatePageSessionId());
const downloadedProjectId = ref("");
const downloadedProjectPath = ref("");
let pollTimer = null;
let unloadCleanupSent = false;

const lineNumbers = computed(() => Array.from({ length: Math.max(1, String(form.codePreview || "").split("\n").length) }, (_, i) => i + 1).join("\n"));
const normalizeStatus = (value) => {
  const s = String(value || "").toUpperCase();
  if (!s) return "待提交";
  if (s === "SUCCESS") return "COMPLETED";
  return s;
};
const taskStatusText = computed(() => normalizeStatus(task.status));
const statusTagType = computed(() => task.status === "SUCCESS" ? "success" : task.status === "FAILED" ? "danger" : task.status === "RUNNING" ? "warning" : "info");
const normalizeRisk = (value) => {
  const t = String(value || "").toUpperCase();
  if (t.includes("HIGH") || t.includes("高")) return "HIGH";
  if (t.includes("MEDIUM") || t.includes("中")) return "MEDIUM";
  if (t.includes("LOW") || t.includes("低")) return "LOW";
  return "INFO";
};
const riskLevelText = computed(() => normalizeRisk(result.reviewSummary?.riskLevel || result.riskLevel));
const riskTagTypeBy = (riskLevel) => riskLevel === "HIGH" ? "danger" : riskLevel === "MEDIUM" ? "warning" : riskLevel === "LOW" ? "success" : "info";
const riskTagType = computed(() => riskTagTypeBy(riskLevelText.value));
const normalizeLines = (text) => String(text || "").split(/\r?\n/).map((v) => v.trim().replace(/^[-*\s]*/, "").replace(/^\d+[.)]\s*/, "")).filter(Boolean);
const issueRows = computed(() => {
  if (Array.isArray(result.reviewIssue) && result.reviewIssue.length) {
    return result.reviewIssue.map((item) => ({
      title: String(item?.title || "未命名问题"),
      description: String(item?.description || ""),
      riskLevel: normalizeRisk(item?.riskLevel),
      issueType: String(item?.issueType || "可维护性问题"),
      filePath: String(item?.filePath || ""),
      directoryPath: String(item?.directoryPath || ""),
      lineStart: Number(item?.lineStart || 0),
      lineEnd: Number(item?.lineEnd || 0),
      suggestion: String(item?.suggestion || ""),
      fromRealLlm: Boolean(item?.fromRealLlm)
    }));
  }
  return normalizeLines(result.issueList).map((line) => ({
    title: line, description: line, riskLevel: normalizeRisk(result.riskLevel), issueType: "可维护性问题",
    filePath: "", directoryPath: "", lineStart: 0, lineEnd: 0, suggestion: "", fromRealLlm: !result.fallbackResult
  }));
});
const suggestionItems = computed(() => Array.from(new Set([...normalizeLines(result.fixSuggestions), ...issueRows.value.map((v) => v.suggestion).filter(Boolean)])));
const summaryText = computed(() => result.reviewSummary?.conclusion || result.summary || "");
const timelineRows = computed(() => Array.isArray(result.timelineLogs) ? result.timelineLogs : []);
const reviewStages = [
  { key: "read-structure", label: "正在读取项目结构", hints: ["结构", "tree", "scan", "read"] },
  { key: "extract-files", label: "正在抽取关键文件", hints: ["关键文件", "extract", "files", "focus"] },
  { key: "call-model", label: "正在调用模型分析", hints: ["模型", "llm", "call", "reason"] },
  { key: "finalize", label: "正在整理审查结果", hints: ["整理", "结果", "summary", "report"] }
];
const resolveStageIndex = (text) => {
  const source = String(text || "").toLowerCase();
  if (!source) return -1;
  return reviewStages.findIndex((stage) => stage.hints.some((hint) => source.includes(hint.toLowerCase())));
};
const currentStageIndex = computed(() => {
  const latest = timelineRows.value.length ? timelineRows.value[timelineRows.value.length - 1] : null;
  const fromTimeline = resolveStageIndex(`${latest?.stage || ""} ${latest?.message || ""}`);
  if (fromTimeline >= 0) return fromTimeline;
  const fromLog = resolveStageIndex(logs.value);
  if (fromLog >= 0) return fromLog;
  const progress = taskProgress.value;
  if (progress >= 80) return 3;
  if (progress >= 55) return 2;
  if (progress >= 25) return 1;
  return 0;
});
const isOutputRunning = computed(() => submitting.value || task.status === "RUNNING");
const outputBannerType = computed(() => {
  if (task.status === "FAILED") return "failed";
  if (task.status === "SUCCESS") return "success";
  if (isOutputRunning.value) return "running";
  return "idle";
});
const outputStatusText = computed(() => {
  if (task.status === "FAILED") return task.message || "审查失败，请查看执行日志。";
  if (task.status === "SUCCESS") return "审查完成，已输出最终结果。";
  if (isOutputRunning.value) return reviewStages[currentStageIndex.value]?.label || "正在审查，请稍候...";
  return "尚未开始审查，点击“开始审查”后将显示实时进度。";
});
const stageState = (index) => {
  if (task.status === "SUCCESS") return "done";
  if (task.status === "FAILED") {
    if (index < currentStageIndex.value) return "done";
    if (index === currentStageIndex.value) return "failed";
    return "pending";
  }
  if (!isOutputRunning.value) return "pending";
  if (index < currentStageIndex.value) return "done";
  if (index === currentStageIndex.value) return "active";
  return "pending";
};
const parseProgressFromLog = (text) => {
  const ms = [...String(text || "").matchAll(/progress=(\d+)%/g)];
  if (!ms.length) return -1;
  return Math.max(0, Math.min(100, Number(ms[ms.length - 1][1] || 0)));
};
const taskProgress = computed(() => {
  const t = timelineRows.value.length ? Number(timelineRows.value[timelineRows.value.length - 1].progress || 0) : -1;
  if (t >= 0) return t;
  const fromLog = parseProgressFromLog(logs.value);
  if (fromLog >= 0) return fromLog;
  if (task.status === "SUCCESS" || task.status === "FAILED") return 100;
  return task.status === "RUNNING" ? 5 : 0;
});
const coverageStats = computed(() => ({
  totalFiles: Number(result.coverageStats?.totalFiles || 0),
  analyzedFiles: Number(result.coverageStats?.analyzedFiles || 0),
  skippedFiles: Number(result.coverageStats?.skippedFiles || 0),
  coverageRate: Number(result.coverageStats?.coverageRate || 0)
}));
const languageTop = computed(() => (Array.isArray(result.languageStats) ? result.languageStats : []).slice(0, 6));
const timelineTagType = (status) => {
  const s = String(status || "").toUpperCase();
  if (s === "DONE" || s === "SUCCESS") return "success";
  if (s === "FAILED" || s === "ERROR") return "danger";
  if (s === "RUNNING") return "warning";
  return "info";
};
const formatTs = (value) => {
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value || "-";
  const p = (v) => String(v).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
};

const buildProjectTree = (structureText) => {
  let nodeSeed = 0;
  const roots = [];
  const pathToId = {};
  const lines = String(structureText || "").split(/\r?\n/);
  for (const raw of lines) {
    let line = raw.trim();
    if (!line || line.startsWith("...")) continue;
    line = line.replace(/^[-*\s]*/, "").replace(/^\d+[.)]\s*/, "").replace(/^[\\/]+/, "").replace(/\\/g, "/").replace(/\/+/g, "/");
    if (!line) continue;
    const segs = line.split("/").filter(Boolean);
    let cursor = roots;
    let parent = "";
    segs.forEach((seg, idx) => {
      const current = parent ? `${parent}/${seg}` : seg;
      let node = cursor.find((v) => v.label === seg);
      if (!node) {
        nodeSeed += 1;
        node = { id: `tree-${nodeSeed}`, label: seg, path: current, isLeaf: false, children: [] };
        cursor.push(node);
      }
      if (idx === segs.length - 1) node.isLeaf = seg.includes(".") || seg.includes("...");
      pathToId[current] = node.id;
      parent = current;
      cursor = node.children;
    });
  }
  return { nodes: roots, pathToId };
};
const findFirstLeafPath = (nodes) => {
  for (const node of nodes) {
    if (node.isLeaf) return node.path;
    const child = findFirstLeafPath(node.children || []);
    if (child) return child;
  }
  return "";
};

const persistProjectSessionState = () => {
  saveCodeReviewProjectSnapshot({
    pageSessionId: pageSessionId.value,
    repoUrl: form.repoUrl,
    downloadedProjectId: downloadedProjectId.value,
    downloadedProjectPath: downloadedProjectPath.value,
    projectStructure: result.projectStructure,
    focusFilePath: form.focusFilePath,
    selectedTreeNodeKey: selectedTreeNodeKey.value,
    selectedTreePath: selectedTreePath.value,
    codePreview: form.codePreview,
    treeData: treeData.value,
    treePathMap: treePathMap.value,
    model: form.model
  });
};

const applyProjectStructure = (structureText, preferred = "") => {
  result.projectStructure = String(structureText || "");
  const payload = buildProjectTree(result.projectStructure);
  treeData.value = payload.nodes;
  treePathMap.value = payload.pathToId;
  expandedTreeKeys.value = treeData.value.map((node) => node.id);
  const target = preferred || form.focusFilePath || findFirstLeafPath(treeData.value);
  form.focusFilePath = target;
  selectedTreeNodeKey.value = target && treePathMap.value[target] ? treePathMap.value[target] : "";
  selectedTreePath.value = target || "";
};

const clearTaskOutputOnly = () => {
  Object.assign(task, { taskId: "", status: "", createdAt: "", updatedAt: "", message: "" });
  logs.value = "";
  Object.assign(result, {
    issueList: "", riskLevel: "", fixSuggestions: "", summary: "",
    fallbackResult: false, fallbackReason: "", resultSource: "", reviewTask: null, reviewSummary: null,
    reviewIssue: [], coverageStats: null, languageStats: [], timelineLogs: []
  });
};
const clearReviewOutput = () => {
  clearTaskOutputOnly();
  result.projectStructure = "";
  treeData.value = [];
  expandedTreeKeys.value = [];
  selectedTreeNodeKey.value = "";
  selectedTreePath.value = "";
  treePathMap.value = {};
};
const resetForm = () => {
  stopPolling();
  Object.assign(form, { repoUrl: "", zipFileName: "", localFolder: "", model: modelOptions[0].value, codePreview: DEFAULT_CODE_PREVIEW, focusFilePath: "" });
  previewMode.value = "default";
  downloadedProjectId.value = "";
  downloadedProjectPath.value = "";
  clearReviewOutput();
  persistProjectSessionState();
};

const handleFileChange = async (file) => {
  const raw = file?.raw;
  if (!raw) return;
  form.zipFileName = raw.name || "";
  form.focusFilePath = form.zipFileName;
  selectedTreePath.value = form.focusFilePath;
  selectedTreeNodeKey.value = "";
  previewMode.value = "upload";
  const name = String(form.zipFileName || "").toLowerCase();
  const canRead = /\.(txt|md|java|py|js|ts|vue|json|xml|yaml|yml|go|c|cc|cpp|h|hpp|cs|rs|kt|swift)$/.test(name) && raw.size <= 2 * 1024 * 1024;
  if (canRead) {
    try {
      form.codePreview = await raw.text();
      persistProjectSessionState();
      return;
    } catch (_) {}
  }
  form.codePreview = `// 已选择文件: ${form.zipFileName}\n// 文件不支持直接预览，可直接开始审查。`;
  persistProjectSessionState();
};

const loadProjectFilePreview = async (path) => {
  if (!downloadedProjectId.value || !pageSessionId.value || !path) return false;
  try {
    const fileResp = await fetchCodeReviewProjectFile({ pageSessionId: pageSessionId.value, projectId: downloadedProjectId.value, path });
    form.codePreview = fileResp.content || `// Empty file: ${path}`;
    form.focusFilePath = fileResp.filePath || path;
    selectedTreePath.value = form.focusFilePath;
    previewMode.value = "repo-file";
    persistProjectSessionState();
    return true;
  } catch (error) {
    ElMessage.error(error.message || "加载文件预览失败");
    return false;
  }
};

const downloadRepository = async () => {
  const repoUrl = String(form.repoUrl || "").trim();
  if (!repoUrl) return ElMessage.warning("请先输入代码仓库地址");
  if (downloadingRepo.value) return;
  downloadingRepo.value = true;
  stopPolling();
  clearTaskOutputOnly();
  try {
    const response = await downloadCodeReviewRepository({ pageSessionId: pageSessionId.value, repoUrl });
    pageSessionId.value = response.pageSessionId || pageSessionId.value;
    form.repoUrl = repoUrl;
    downloadedProjectId.value = response.projectId || "";
    downloadedProjectPath.value = response.localPath || "";
    form.localFolder = downloadedProjectPath.value;
    previewMode.value = "repo-file";
    applyProjectStructure(response.projectStructure || "", response.focusFilePath || "");
    form.codePreview = response.focusFileContent || `// 仓库下载完成: ${repoUrl}`;
    form.focusFilePath = response.focusFilePath || form.focusFilePath;
    if (form.focusFilePath && treePathMap.value[form.focusFilePath]) selectedTreeNodeKey.value = treePathMap.value[form.focusFilePath];
    persistProjectSessionState();
    ElMessage.success(response.reused ? "已复用本页面会话中的临时项目" : "仓库下载完成，项目树与代码区已更新");
  } catch (error) {
    ElMessage.error(error.message || "仓库下载失败");
  } finally {
    downloadingRepo.value = false;
  }
};

const syncGutterScroll = (event) => { if (gutterRef.value) gutterRef.value.scrollTop = event.target.scrollTop; };
const updateCodePreviewFromResult = () => {
  if (previewMode.value === "upload" || previewMode.value === "repo-file") return;
  const findings = issueRows.value.slice(0, 5).map((item, i) => `// ${i + 1}. [${item.riskLevel}] ${item.title}`);
  form.codePreview = [`// Focus file: ${form.focusFilePath || "-"}`, `// Repository: ${form.repoUrl || "-"}`, `// Model: ${result.reviewTask?.model || form.model}`, `// Source: ${result.fallbackResult ? "FALLBACK" : "REAL_LLM"}`, "", ...findings, "", summaryText.value ? `// Summary: ${summaryText.value}` : "// 等待审查结论..."].join("\n");
};
const handleTreeNodeClick = async (node) => {
  if (!node) return;
  selectedTreeNodeKey.value = node.id || "";
  selectedTreePath.value = node.path || node.label || "";
  if (!node.path) return persistProjectSessionState();
  form.focusFilePath = node.path;
  if (node.isLeaf && downloadedProjectId.value) return void (await loadProjectFilePreview(node.path));
  if (previewMode.value !== "upload") {
    form.codePreview = [`// Focus file: ${node.path}`, `// Repository: ${form.repoUrl || "-"}`, `// Model: ${result.reviewTask?.model || form.model}`, "", "// 请选择具体文件节点以加载真实源码。"].join("\n");
  }
  persistProjectSessionState();
};

const applyResult = (resp = {}) => {
  result.projectStructure = String(resp.projectStructure || "");
  result.issueList = String(resp.issueList || "");
  result.riskLevel = String(resp.riskLevel || "");
  result.fixSuggestions = String(resp.fixSuggestions || "");
  result.summary = String(resp.summary || "");
  result.fallbackResult = Boolean(resp.fallbackResult);
  result.fallbackReason = String(resp.fallbackReason || "");
  result.resultSource = String(resp.resultSource || (resp.fallbackResult ? "FALLBACK" : ""));
  result.reviewTask = resp.reviewTask || null;
  result.reviewSummary = resp.reviewSummary || null;
  result.reviewIssue = Array.isArray(resp.reviewIssue) ? resp.reviewIssue : [];
  result.coverageStats = resp.coverageStats || null;
  result.languageStats = Array.isArray(resp.languageStats) ? resp.languageStats : [];
  result.timelineLogs = Array.isArray(resp.timelineLogs) ? resp.timelineLogs : [];
};
const updateTaskState = async () => {
  if (!task.taskId) return;
  const [detail, logResp, resultResp] = await Promise.all([fetchTaskDetail(task.taskId), fetchTaskLogs(task.taskId), fetchCodeReviewResult(task.taskId)]);
  task.status = detail.status || "";
  task.createdAt = detail.createdAt || "";
  task.updatedAt = detail.updatedAt || "";
  task.message = detail.message || "";
  logs.value = logResp.content || "";
  applyResult(resultResp);
  if (result.projectStructure) applyProjectStructure(result.projectStructure, form.focusFilePath || "");
  updateCodePreviewFromResult();
  persistProjectSessionState();
  if (task.status === "SUCCESS" || task.status === "FAILED") stopPolling();
};
const startPolling = () => {
  stopPolling();
  pollTimer = setInterval(() => {
    void updateTaskState().catch(() => stopPolling());
  }, 2000);
};
const stopPolling = () => { if (pollTimer) { clearInterval(pollTimer); pollTimer = null; } };
const scrollToOutputCard = async () => {
  await nextTick();
  const target = outputCardRef.value?.$el || outputCardRef.value;
  if (target && typeof target.scrollIntoView === "function") {
    target.scrollIntoView({ behavior: "smooth", block: "start" });
  }
};
const submitTask = async () => {
  const hasRepo = Boolean(String(form.repoUrl || "").trim());
  const hasUploaded = Boolean(form.zipFileName);
  const hasDownloaded = Boolean(downloadedProjectId.value);
  if (!hasRepo && !hasUploaded && !hasDownloaded) return ElMessage.warning("请先输入仓库地址、上传文件或下载项目");
  submitting.value = true;
  await scrollToOutputCard();
  stopPolling();
  clearTaskOutputOnly();
  if (previewMode.value !== "upload" && previewMode.value !== "repo-file") {
    previewMode.value = "repo";
    form.codePreview = [`// Repository: ${form.repoUrl || "-"}`, `// Model: ${form.model}`, "", "// 审查任务已提交，等待后端真实审查链路执行..."].join("\n");
  }
  try {
    const response = await submitCodeReviewTask({
      repoUrl: String(form.repoUrl || "").trim(),
      zipFileName: form.zipFileName,
      localFolder: downloadedProjectPath.value || form.localFolder,
      model: form.model,
      parameters: `--model ${form.model}`
    });
    task.taskId = response.taskId || "";
    task.status = response.status || "";
    ElMessage.success("审查任务已提交");
    await updateTaskState();
    startPolling();
    persistProjectSessionState();
  } catch (error) {
    ElMessage.error(error.message || "提交失败");
  } finally {
    submitting.value = false;
  }
};

const sendUnloadCleanup = () => {
  if (unloadCleanupSent) return;
  unloadCleanupSent = true;
  const payload = { pageSessionId: pageSessionId.value, projectId: downloadedProjectId.value };
  if (!payload.pageSessionId) {
    clearCodeReviewProjectSnapshot();
    return;
  }
  clearCodeReviewProjectSnapshot();
  const body = JSON.stringify(payload);
  let delivered = false;
  if (typeof navigator !== "undefined" && typeof navigator.sendBeacon === "function") {
    try {
      delivered = navigator.sendBeacon(`${API_BASE}/api/code-review/projects/cleanup-beacon`, new Blob([body], { type: "application/json" }));
    } catch (_) {}
  }
  if (!delivered) void cleanupCodeReviewProject(payload, { keepalive: true }).catch(() => {});
};
const restoreProjectSessionState = () => {
  const snapshot = loadCodeReviewProjectSnapshot();
  if (!snapshot) {
    pageSessionId.value = generatePageSessionId();
    return persistProjectSessionState();
  }
  pageSessionId.value = snapshot.pageSessionId || generatePageSessionId();
  form.repoUrl = snapshot.repoUrl || "";
  form.model = snapshot.model || form.model;
  downloadedProjectId.value = snapshot.downloadedProjectId || "";
  downloadedProjectPath.value = snapshot.downloadedProjectPath || "";
  form.localFolder = downloadedProjectPath.value;
  if (snapshot.projectStructure) applyProjectStructure(snapshot.projectStructure, snapshot.focusFilePath || "");
  else {
    treeData.value = Array.isArray(snapshot.treeData) ? snapshot.treeData : [];
    treePathMap.value = snapshot.treePathMap && typeof snapshot.treePathMap === "object" ? snapshot.treePathMap : {};
  }
  selectedTreeNodeKey.value = snapshot.selectedTreeNodeKey || "";
  selectedTreePath.value = snapshot.selectedTreePath || "";
  form.focusFilePath = snapshot.focusFilePath || "";
  form.codePreview = snapshot.codePreview || DEFAULT_CODE_PREVIEW;
  if (downloadedProjectId.value) previewMode.value = "repo-file";
  persistProjectSessionState();
};

onMounted(() => {
  restoreProjectSessionState();
  window.addEventListener("beforeunload", sendUnloadCleanup);
  window.addEventListener("pagehide", sendUnloadCleanup);
});
onBeforeUnmount(() => {
  stopPolling();
  window.removeEventListener("beforeunload", sendUnloadCleanup);
  window.removeEventListener("pagehide", sendUnloadCleanup);
  persistProjectSessionState();
});
</script>

<style scoped>
.code-review-workbench,.code-review-grid,.code-review-operation,.code-review-display,.code-review-output,.cr-task-card,.cr-tree-card,.cr-output-card{height:100%;min-height:0}
.code-review-grid{gap:12px;--cr-top-panel-height:clamp(700px,calc(100vh - 150px),980px);grid-template-rows:var(--cr-top-panel-height) auto;align-items:start}
.code-review-operation,.code-review-display{min-height:var(--cr-top-panel-height)}
.code-review-output{grid-column:1 / -1;min-height:360px}
.cr-task-card,.cr-tree-card{height:var(--cr-top-panel-height);min-height:var(--cr-top-panel-height);max-height:var(--cr-top-panel-height)}
.cr-output-card{width:100%;min-height:360px}
.cr-task-card :deep(.el-card__body){display:flex;flex-direction:column;min-height:0;overflow:hidden}
.cr-tree-card :deep(.el-card__body){display:flex;flex-direction:column;min-height:0;overflow:hidden}
.cr-output-card :deep(.el-card__body){min-height:0;overflow:auto}
.cr-task-card{--cr-side-chip-width:220px}
.cr-task-card .cr-config-toolbar{display:grid;grid-template-columns:var(--cr-side-chip-width) minmax(0,1fr) var(--cr-side-chip-width);column-gap:14px;align-items:stretch;width:100%}
.cr-task-card .cr-config-toolbar>.cr-upload-chip,.cr-task-card .cr-config-toolbar>.cr-model-chip{width:var(--cr-side-chip-width);min-width:var(--cr-side-chip-width);max-width:var(--cr-side-chip-width)}
.cr-task-card .cr-config-toolbar>.cr-upload-chip{justify-self:start}
.cr-task-card .cr-config-toolbar>.cr-model-chip{justify-self:end}
.cr-task-card .cr-config-toolbar>.cr-repo-chip{width:100%;min-width:0;max-width:none;justify-self:stretch;display:flex;align-items:center;gap:8px}
.cr-task-card .cr-config-toolbar>.cr-repo-chip .wb-config-chip-label{flex:none}
.cr-task-card .cr-config-toolbar>.cr-repo-chip :deep(.cr-repo-url-control){flex:1 1 auto;width:auto;min-width:0;max-width:none}
.cr-task-card .cr-config-toolbar>.cr-repo-chip :deep(.cr-repo-url-control .el-input__wrapper){width:100%}
.cr-task-card .cr-config-toolbar>.cr-repo-chip :deep(.cr-repo-url-control .el-input__inner){min-width:0}
.cr-task-card .cr-config-toolbar>.cr-repo-chip .cr-repo-download{flex:0 0 78px;width:78px;min-width:78px;max-width:78px}
.cr-task-card .wb-code-group{display:grid;grid-template-rows:auto minmax(0,1fr) auto;flex:1 1 auto;min-height:0;margin-bottom:10px}
.cr-task-card .wb-main-actions{margin-top:0;flex:none}
.cr-code-editor{height:100%;min-height:0;max-height:none}
.cr-code-caption{font-size:12px;color:#5f7596}
.cr-tree-scroll{height:auto;min-height:0;flex:1 1 auto;border:1px solid rgba(44,107,255,.14);border-radius:14px;background:#f7fbff;padding:10px;overflow:auto}
.cr-stage-banner{display:flex;align-items:center;gap:8px;padding:9px 12px;border-radius:12px;border:1px solid rgba(44,107,255,.18);background:rgba(247,251,255,.95);color:#325987;font-size:12px}
.cr-stage-banner.is-running{border-color:rgba(44,107,255,.28);background:rgba(233,243,255,.9);color:#1f4b82}
.cr-stage-banner.is-success{border-color:rgba(34,189,126,.24);background:rgba(233,250,242,.9);color:#1f7a52}
.cr-stage-banner.is-failed{border-color:rgba(234,106,118,.28);background:rgba(255,241,243,.92);color:#9c3d47}
.cr-stage-flow{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin-top:10px}
.cr-stage-item{display:flex;align-items:center;gap:8px;padding:8px 10px;border-radius:10px;border:1px solid rgba(44,107,255,.14);background:#f8fbff;color:#5f7596;font-size:12px}
.cr-stage-item.is-active{border-color:rgba(44,107,255,.34);background:rgba(235,245,255,.95);color:#1f4b82}
.cr-stage-item.is-done{border-color:rgba(34,189,126,.24);background:rgba(234,250,243,.92);color:#1f7a52}
.cr-stage-item.is-failed{border-color:rgba(234,106,118,.26);background:rgba(255,242,244,.92);color:#9c3d47}
.cr-stage-index{display:inline-flex;align-items:center;justify-content:center;flex:0 0 18px;width:18px;height:18px;border-radius:999px;background:rgba(44,107,255,.16);color:#2d5f94;font-size:11px;font-weight:600}
.cr-stage-item.is-done .cr-stage-index{background:rgba(34,189,126,.2);color:#1f7a52}
.cr-stage-item.is-failed .cr-stage-index{background:rgba(234,106,118,.2);color:#9c3d47}
.cr-stage-name{line-height:1.35}
.cr-overview-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.cr-overview-item{border:1px solid rgba(44,107,255,.14);border-radius:12px;background:#f7fbff;padding:10px 12px;display:grid;gap:6px}
.cr-progress-item{grid-column:span 2}
.cr-fallback-alert{margin-top:10px}
.cr-meta-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
.cr-meta-grid>div{border:1px solid rgba(44,107,255,.14);border-radius:10px;background:#f9fcff;padding:8px 10px;display:grid;gap:4px}
.cr-meta-grid span{font-size:12px;color:#5f7596}
.cr-meta-grid strong{font-size:12px;color:#1f4b82;line-height:1.4;word-break:break-all}
.cr-lang-tags{display:flex;flex-wrap:wrap;gap:6px;margin-top:10px}
.cr-issue-list{display:grid;gap:10px}
.cr-issue-item{border:1px solid rgba(44,107,255,.16);border-radius:12px;padding:10px 12px;background:#f8fbff;display:grid;gap:6px}
.cr-issue-item header{display:flex;justify-content:space-between;align-items:center;gap:10px;color:#1f4b82;font-size:13px;font-weight:600}
.cr-issue-item p{margin:0;color:#355f8f;font-size:12px;line-height:1.55}
.cr-issue-meta{display:flex;flex-wrap:wrap;gap:10px;color:#5d7598;font-size:11px}
.cr-list{margin:0;padding-left:20px;color:#28527f;font-size:12px;line-height:1.6}
.cr-empty{border:1px dashed rgba(44,107,255,.22);border-radius:12px;background:rgba(247,251,255,.9);color:#5f7596;font-size:12px;padding:10px 12px}
.cr-timeline-list{margin:0;padding:0;list-style:none;display:grid;gap:8px}
.cr-timeline-list li{display:grid;grid-template-columns:auto auto auto auto 1fr;align-items:center;gap:8px;padding:8px 10px;border:1px solid rgba(44,107,255,.15);border-radius:10px;background:#f8fbff;font-size:12px;color:#355f8f}
.cr-log-preview{min-height:120px;max-height:220px}
@media (max-width:1200px){.cr-task-card{--cr-side-chip-width:220px}.cr-task-card .cr-config-toolbar{grid-template-columns:var(--cr-side-chip-width) minmax(0,1fr) var(--cr-side-chip-width)}.cr-task-card .cr-config-toolbar>.cr-upload-chip,.cr-task-card .cr-config-toolbar>.cr-model-chip{width:var(--cr-side-chip-width);min-width:var(--cr-side-chip-width);max-width:var(--cr-side-chip-width)}}
@media (max-width:1080px){.code-review-grid{grid-template-columns:1fr;grid-template-rows:auto;--cr-top-panel-height:auto}.code-review-operation,.code-review-display{min-height:0}.code-review-output{grid-column:1 / 2}.cr-task-card,.cr-tree-card{height:auto;min-height:0}}
@media (max-width:720px){.cr-task-card .cr-config-toolbar{grid-template-columns:1fr}.cr-upload-chip,.cr-repo-chip,.cr-model-chip{width:100%;min-width:0;max-width:none;justify-self:stretch}.cr-overview-grid,.cr-meta-grid,.cr-stage-flow{grid-template-columns:1fr}.cr-progress-item{grid-column:span 1}.cr-timeline-list li{grid-template-columns:1fr}}
</style>
