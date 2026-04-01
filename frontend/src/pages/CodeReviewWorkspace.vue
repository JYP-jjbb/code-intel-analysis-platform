<template>
  <div class="nutera-workbench code-review-workbench">
    <div class="nutera-grid code-review-grid">
      <section class="nutera-operation code-review-operation">
        <el-card class="wb-card wb-task-config-card cr-task-card" shadow="never">
          <template #header>
            <div class="wb-card-head">
              <h3>审查任务配置</h3>
            </div>
          </template>

          <div class="cr-config-row">
            <div class="wb-config-toolbar cr-config-toolbar">
              <div class="wb-config-chip cr-upload-chip">
                <span class="wb-config-chip-label">文件上传</span>
                <el-upload
                  class="wb-chip-upload"
                  :auto-upload="false"
                  :show-file-list="false"
                  :on-change="handleFileChange"
                >
                  <el-button class="wb-chip-upload-btn" size="small">
                    <el-icon><UploadFilled /></el-icon>
                    <span>选择文件</span>
                  </el-button>
                </el-upload>
              </div>

              <div class="wb-config-chip is-repo-chip cr-repo-chip">
                <span class="wb-config-chip-label cr-repo-label">代码仓库</span>
                <el-input
                  v-model="form.repoUrl"
                  class="wb-chip-control cr-repo-input"
                  size="small"
                  placeholder="https://github.com/..."
                />
                <el-button class="cr-repo-download" size="small" :loading="downloadingRepo" @click="downloadRepository">下载</el-button>
              </div>

              <div class="wb-config-chip cr-model-chip">
                <span class="wb-config-chip-label">模型配置</span>
                <el-select
                  v-model="form.model"
                  class="wb-chip-control"
                  size="small"
                  placeholder="选择模型"
                  popper-class="wb-config-select-dropdown"
                >
                  <el-option
                    v-for="item in modelOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </div>
            </div>
          </div>

          <div class="wb-form-group wb-code-group">
            <label class="wb-field-label">代码展示区 / 项目入口代码展示区</label>
            <div class="wb-code-editor cr-code-editor">
              <pre ref="gutterRef" class="wb-code-gutter" aria-hidden="true">{{ lineNumbers }}</pre>
              <textarea
                v-model="form.codePreview"
                class="wb-code-textarea"
                spellcheck="false"
                readonly
                @scroll="syncGutterScroll"
              />
            </div>
            <div class="cr-code-caption">当前重点文件：{{ form.focusFilePath || "-" }}</div>
          </div>

          <div class="wb-action-row wb-main-actions">
            <el-button class="wb-primary-btn" type="primary" :loading="submitting" @click="submitTask">
              <el-icon><Check /></el-icon>
              启动审查
            </el-button>
            <el-button class="wb-soft-btn" @click="resetForm">
              <el-icon><Brush /></el-icon>
              清空
            </el-button>
          </div>
        </el-card>
      </section>

      <section class="nutera-display code-review-display">
        <el-card class="wb-card wb-log-card cr-tree-card" shadow="never">
          <template #header>
            <div class="wb-card-head">
              <h3>项目树</h3>
              <p>{{ selectedTreePath || "等待项目结构输出" }}</p>
            </div>
          </template>

          <div class="wb-block wb-block-log-only">
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
              <el-empty v-else description="尚无项目结构数据" />
            </div>
          </div>
        </el-card>

        <el-card class="wb-card wb-summary-card cr-result-card" shadow="never">
          <template #header>
            <div class="wb-card-head">
              <h3>审查结果</h3>
            </div>
          </template>

          <div class="wb-block">
            <div class="wb-block-title">风险概览</div>
            <div class="cr-overview-grid">
              <div class="cr-overview-item">
                <span class="cr-overview-key">任务状态</span>
                <el-tag size="small" :type="statusTagType">{{ taskStatusText }}</el-tag>
              </div>
              <div class="cr-overview-item">
                <span class="cr-overview-key">风险等级</span>
                <el-tag size="small" :type="riskTagType">{{ riskLevelText }}</el-tag>
              </div>
              <div class="cr-overview-item">
                <span class="cr-overview-key">问题总数</span>
                <strong class="cr-overview-value">{{ issueItems.length }}</strong>
              </div>
              <div class="cr-overview-item">
                <span class="cr-overview-key">模型配置</span>
                <strong class="cr-overview-value">{{ form.model }}</strong>
              </div>
            </div>
          </div>

          <div class="wb-block">
            <div class="wb-block-title">问题列表</div>
            <ul v-if="issueItems.length" class="cr-list">
              <li v-for="(item, index) in issueItems" :key="`issue-${index}`">{{ item }}</li>
            </ul>
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
            <pre class="wb-code-block">{{ result.summary || "等待总体审查结论..." }}</pre>
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
import { computed, h, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Brush, Check, UploadFilled } from "@element-plus/icons-vue";
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

const DEFAULT_CODE_PREVIEW = `// Upload source file or input GitHub repository URL, then click "启动审查".
// This editor displays the selected file source and focus entry code.
// The right panel shows project tree and review feedback.`;

const treeProps = {
  label: "label",
  children: "children"
};

const modelOptions = [
  { label: "Kimi", value: "kimi-k2.5" },
  { label: "DeepSeek", value: "deepseek-ai/DeepSeek-V3.2" },
  { label: "Hunyuan", value: "hunyuan-2.0-thinking-20251109" },
  { label: "Qwen", value: "qwen3.5-plus" }
];

const form = reactive({
  repoUrl: "",
  zipFileName: "",
  localFolder: "",
  model: "kimi-k2.5",
  codePreview: DEFAULT_CODE_PREVIEW,
  focusFilePath: ""
});

const task = reactive({
  taskId: "",
  status: "",
  createdAt: "",
  updatedAt: "",
  message: ""
});

const logs = ref("");
const result = reactive({
  projectStructure: "",
  issueList: "",
  riskLevel: "",
  fixSuggestions: "",
  summary: ""
});

const gutterRef = ref(null);
const submitting = ref(false);
const downloadingRepo = ref(false);
const treeData = ref([]);
const expandedTreeKeys = ref([]);
const selectedTreeNodeKey = ref("");
const selectedTreePath = ref("");
const previewMode = ref("default");
const treePathMap = ref({});

const pageSessionId = ref(generatePageSessionId());
const downloadedProjectId = ref("");
const downloadedProjectPath = ref("");
let pollTimer = null;
let unloadCleanupSent = false;

const lineNumbers = computed(() => {
  const lineCount = Math.max(1, String(form.codePreview || "").split("\n").length);
  return Array.from({ length: lineCount }, (_, index) => index + 1).join("\n");
});

const taskStatusText = computed(() => {
  if (!task.status) return "待提交";
  if (task.status === "SUCCESS") return "COMPLETED";
  return task.status;
});

const statusTagType = computed(() => {
  if (task.status === "SUCCESS") return "success";
  if (task.status === "FAILED") return "danger";
  if (task.status === "RUNNING") return "warning";
  return "info";
});

const riskLevelText = computed(() => String(result.riskLevel || "UNKNOWN").toUpperCase());

const riskTagType = computed(() => {
  if (riskLevelText.value === "HIGH") return "danger";
  if (riskLevelText.value === "MEDIUM") return "warning";
  if (riskLevelText.value === "LOW") return "success";
  return "info";
});

const issueItems = computed(() => normalizeList(result.issueList));
const suggestionItems = computed(() => normalizeList(result.fixSuggestions));

const normalizeList = (text) => String(text || "")
  .split(/\r?\n/)
  .map((line) => line.trim())
  .map((line) => line.replace(/^[-*•\s]*/, ""))
  .map((line) => line.replace(/^\d+[\.\)]\s*/, ""))
  .filter((line) => line.length > 0);

const buildProjectTree = (structureText) => {
  let nodeSeed = 0;
  const roots = [];
  const pathToId = {};

  const insertPath = (pathText) => {
    const segments = pathText.split("/").filter(Boolean);
    if (!segments.length) return;
    let parentPath = "";
    let cursor = roots;
    for (let i = 0; i < segments.length; i += 1) {
      const segment = segments[i];
      const currentPath = parentPath ? `${parentPath}/${segment}` : segment;
      let node = cursor.find((item) => item.label === segment);
      if (!node) {
        nodeSeed += 1;
        node = {
          id: `tree-${nodeSeed}`,
          label: segment,
          path: currentPath,
          isLeaf: false,
          children: []
        };
        cursor.push(node);
      }
      if (i === segments.length - 1) {
        node.isLeaf = segment.includes(".") || segment.includes("...");
      }
      pathToId[currentPath] = node.id;
      parentPath = currentPath;
      cursor = node.children;
    }
  };

  const lines = String(structureText || "").split(/\r?\n/);
  for (const rawLine of lines) {
    let line = rawLine.trim();
    if (!line || line.startsWith("...")) continue;
    line = line.replace(/^[-*•\s]*/, "");
    line = line.replace(/^\d+[\.\)]\s*/, "");
    line = line.replace(/^[\\/]+/, "");
    line = line.replace(/\\/g, "/");
    line = line.replace(/\/+/g, "/");
    if (!line) continue;
    insertPath(line);
  }

  const sortNodes = (nodes) => {
    nodes.sort((a, b) => {
      if (a.isLeaf !== b.isLeaf) return a.isLeaf ? 1 : -1;
      return a.label.localeCompare(b.label);
    });
    for (const node of nodes) {
      if (Array.isArray(node.children) && node.children.length) {
        sortNodes(node.children);
      }
    }
  };

  sortNodes(roots);
  return { nodes: roots, pathToId };
};

const findFirstLeafPath = (nodes) => {
  for (const node of nodes) {
    if (node.isLeaf) return node.path;
    const fromChild = findFirstLeafPath(node.children || []);
    if (fromChild) return fromChild;
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
    treePathMap: treePathMap.value
  });
};

const applyProjectStructure = (structureText, preferredFocusPath = "") => {
  result.projectStructure = String(structureText || "");
  const treePayload = buildProjectTree(result.projectStructure);
  treeData.value = treePayload.nodes;
  treePathMap.value = treePayload.pathToId;
  expandedTreeKeys.value = treeData.value.map((node) => node.id);

  const fallbackPath = findFirstLeafPath(treeData.value);
  const targetPath = preferredFocusPath || form.focusFilePath || fallbackPath;
  form.focusFilePath = targetPath;
  if (targetPath && treePathMap.value[targetPath]) {
    selectedTreeNodeKey.value = treePathMap.value[targetPath];
    selectedTreePath.value = targetPath;
  } else {
    selectedTreeNodeKey.value = "";
    selectedTreePath.value = "";
  }
};

const clearTaskOutputOnly = () => {
  task.taskId = "";
  task.status = "";
  task.createdAt = "";
  task.updatedAt = "";
  task.message = "";
  logs.value = "";
  result.issueList = "";
  result.riskLevel = "";
  result.fixSuggestions = "";
  result.summary = "";
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
  form.repoUrl = "";
  form.zipFileName = "";
  form.localFolder = "";
  form.model = modelOptions[0].value;
  form.codePreview = DEFAULT_CODE_PREVIEW;
  form.focusFilePath = "";
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
  const textFilePattern = /\.(txt|md|java|py|js|ts|vue|json|xml|yaml|yml|go|c|cc|cpp|h|hpp|cs|rs|kt|swift)$/;
  const canReadAsText = textFilePattern.test(name) && raw.size <= 2 * 1024 * 1024;

  if (canReadAsText) {
    try {
      const content = await raw.text();
      form.codePreview = content || `// Selected file ${form.zipFileName}\n// File content is empty`;
      persistProjectSessionState();
      return;
    } catch (_) {
      // fall through
    }
  }

  form.codePreview = `// Selected file: ${form.zipFileName}
// This file appears binary or archive and cannot be previewed directly.
// You can still start review and inspect generated findings.`;
  persistProjectSessionState();
};

const loadProjectFilePreview = async (path, { silent = false } = {}) => {
  if (!downloadedProjectId.value || !pageSessionId.value || !path) {
    return false;
  }
  try {
    const fileResp = await fetchCodeReviewProjectFile({
      pageSessionId: pageSessionId.value,
      projectId: downloadedProjectId.value,
      path
    });
    form.codePreview = fileResp.content || `// Empty file: ${path}`;
    form.focusFilePath = fileResp.filePath || path;
    selectedTreePath.value = form.focusFilePath;
    previewMode.value = "repo-file";
    persistProjectSessionState();
    return true;
  } catch (error) {
    if (!silent) {
      ElMessage.error(error.message || "Failed to load file preview");
    }
    return false;
  }
};

const openDownloadErrorDialog = async (error) => {
  const message = String(error?.message || "仓库下载失败");
  const detail = String(error?.detail || "").trim();
  const hints = Array.isArray(error?.hints) ? error.hints.map((item) => String(item || "").trim()).filter(Boolean) : [];

  if (!detail && !hints.length) {
    ElMessage.error(message);
    return;
  }

  const nodes = [
    h("p", { style: "margin: 0 0 8px; font-weight: 600;" }, message)
  ];

  if (hints.length) {
    nodes.push(
      h(
        "ul",
        { style: "margin: 0 0 8px; padding-left: 18px;" },
        hints.map((item) => h("li", { style: "margin: 2px 0;" }, item))
      )
    );
  }

  if (detail) {
    nodes.push(
      h("details", { style: "margin-top: 6px;" }, [
        h("summary", { style: "cursor: pointer;" }, "查看原始 stderr"),
        h(
          "pre",
          {
            style: "margin-top: 8px; max-height: 240px; overflow: auto; white-space: pre-wrap; word-break: break-all; background: #f5f7fa; padding: 10px; border-radius: 6px;"
          },
          detail
        )
      ])
    );
  }

  await ElMessageBox.alert(h("div", nodes), "仓库下载失败", {
    confirmButtonText: "确定"
  }).catch(() => {});
};

const downloadRepository = async () => {
  const repoUrl = String(form.repoUrl || "").trim();
  if (!repoUrl) {
    ElMessage.warning("请先输入代码仓库地址");
    return;
  }
  if (downloadingRepo.value) {
    return;
  }

  downloadingRepo.value = true;
  stopPolling();
  clearTaskOutputOnly();
  try {
    const response = await downloadCodeReviewRepository({
      pageSessionId: pageSessionId.value,
      repoUrl
    });

    pageSessionId.value = response.pageSessionId || pageSessionId.value;
    form.repoUrl = repoUrl;
    downloadedProjectId.value = response.projectId || "";
    downloadedProjectPath.value = response.localPath || "";
    form.localFolder = downloadedProjectPath.value;
    previewMode.value = "repo-file";

    applyProjectStructure(response.projectStructure || "", response.focusFilePath || "");
    form.codePreview = response.focusFileContent || `// Repository downloaded: ${repoUrl}\n// Focus file preview unavailable`;
    form.focusFilePath = response.focusFilePath || form.focusFilePath;
    selectedTreePath.value = form.focusFilePath;
    if (form.focusFilePath && treePathMap.value[form.focusFilePath]) {
      selectedTreeNodeKey.value = treePathMap.value[form.focusFilePath];
    }

    persistProjectSessionState();
    if (response.reused) {
      ElMessage.success("已复用当前页面会话中的临时项目");
    } else {
      ElMessage.success("仓库下载完成，项目树与入口代码已更新");
    }
  } catch (error) {
    ElMessage.error(error.message || "仓库下载失败");
  } finally {
    downloadingRepo.value = false;
  }
};

const syncGutterScroll = (event) => {
  if (!gutterRef.value) return;
  gutterRef.value.scrollTop = event.target.scrollTop;
};

const updateCodePreviewFromResult = () => {
  if (previewMode.value === "upload" || previewMode.value === "repo-file") {
    return;
  }
  const findings = issueItems.value.slice(0, 6);
  form.codePreview = [
    `// Focus file: ${form.focusFilePath || "-"}`,
    `// Repository: ${form.repoUrl || "-"}`,
    `// Model: ${form.model}`,
    "",
    findings.length ? "// Key findings:" : "// No key findings yet.",
    ...findings.map((item, index) => `// ${index + 1}. ${item}`),
    "",
    result.summary ? `// Summary: ${result.summary}` : "// Summary pending..."
  ].join("\n");
};

const handleTreeNodeClick = async (node) => {
  if (!node) return;
  selectedTreeNodeKey.value = node.id || "";
  selectedTreePath.value = node.path || node.label || "";
  if (!node.path) {
    persistProjectSessionState();
    return;
  }
  form.focusFilePath = node.path;
  if (node.isLeaf && downloadedProjectId.value) {
    await loadProjectFilePreview(node.path, { silent: false });
    return;
  }

  if (previewMode.value !== "upload") {
    const firstIssue = issueItems.value[0] || "No related issue yet.";
    form.codePreview = [
      `// Focus file: ${node.path}`,
      `// Repository: ${form.repoUrl || "-"}`,
      `// Model: ${form.model}`,
      "",
      "// Select a leaf file to load real source preview.",
      `// Related finding: ${firstIssue}`
    ].join("\n");
  }
  persistProjectSessionState();
};

const updateTaskState = async () => {
  if (!task.taskId) return;
  const [detail, logResp, resultResp] = await Promise.all([
    fetchTaskDetail(task.taskId),
    fetchTaskLogs(task.taskId),
    fetchCodeReviewResult(task.taskId)
  ]);

  task.status = detail.status || "";
  task.createdAt = detail.createdAt || "";
  task.updatedAt = detail.updatedAt || "";
  task.message = detail.message || "";
  logs.value = logResp.content || "";

  result.issueList = resultResp.issueList || "";
  result.riskLevel = resultResp.riskLevel || "";
  result.fixSuggestions = resultResp.fixSuggestions || "";
  result.summary = resultResp.summary || "";

  const returnedStructure = String(resultResp.projectStructure || "").trim();
  if (returnedStructure) {
    applyProjectStructure(returnedStructure, form.focusFilePath || "");
  } else if (!treeData.value.length && result.projectStructure) {
    applyProjectStructure(result.projectStructure, form.focusFilePath || "");
  }

  updateCodePreviewFromResult();
  persistProjectSessionState();

  if (task.status === "SUCCESS" || task.status === "FAILED") {
    stopPolling();
  }
};

const startPolling = () => {
  stopPolling();
  pollTimer = setInterval(() => {
    void updateTaskState().catch(() => {
      stopPolling();
    });
  }, 2000);
};

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
};

const submitTask = async () => {
  const hasRepo = Boolean(String(form.repoUrl || "").trim());
  const hasUploaded = Boolean(form.zipFileName);
  const hasDownloadedProject = Boolean(downloadedProjectId.value);

  if (!hasRepo && !hasUploaded && !hasDownloadedProject) {
    ElMessage.warning("请输入代码仓库地址、上传文件或先下载仓库项目");
    return;
  }

  submitting.value = true;
  stopPolling();
  clearTaskOutputOnly();
  if (previewMode.value !== "upload" && previewMode.value !== "repo-file") {
    previewMode.value = "repo";
    form.codePreview = [
      `// Repository: ${form.repoUrl || "-"}`,
      `// Model: ${form.model}`,
      "",
      "// Review task started...",
      "// Waiting for analyzer feedback."
    ].join("\n");
  }

  try {
    const payload = {
      repoUrl: String(form.repoUrl || "").trim(),
      zipFileName: form.zipFileName,
      localFolder: downloadedProjectPath.value || form.localFolder,
      model: form.model,
      parameters: `--model ${form.model}`
    };
    const response = await submitCodeReviewTask(payload);
    task.taskId = response.taskId || "";
    task.status = response.status || "";
    task.message = "";
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

const buildCleanupPayload = () => ({
  pageSessionId: pageSessionId.value,
  projectId: downloadedProjectId.value
});

const sendUnloadCleanup = () => {
  if (unloadCleanupSent) {
    return;
  }
  unloadCleanupSent = true;
  const payload = buildCleanupPayload();
  if (!payload.pageSessionId) {
    clearCodeReviewProjectSnapshot();
    return;
  }

  clearCodeReviewProjectSnapshot();
  const body = JSON.stringify(payload);
  let delivered = false;

  if (typeof navigator !== "undefined" && typeof navigator.sendBeacon === "function") {
    try {
      const blob = new Blob([body], { type: "application/json" });
      delivered = navigator.sendBeacon(`${API_BASE}/api/code-review/projects/cleanup-beacon`, blob);
    } catch (_) {
      delivered = false;
    }
  }

  if (!delivered) {
    void cleanupCodeReviewProject(payload, { keepalive: true }).catch(() => {});
  }
};

const handleBeforeUnload = () => {
  sendUnloadCleanup();
};

const handlePageHide = () => {
  sendUnloadCleanup();
};

const restoreProjectSessionState = () => {
  const snapshot = loadCodeReviewProjectSnapshot();
  if (!snapshot) {
    pageSessionId.value = generatePageSessionId();
    persistProjectSessionState();
    return;
  }

  pageSessionId.value = snapshot.pageSessionId || generatePageSessionId();
  form.repoUrl = snapshot.repoUrl || "";
  downloadedProjectId.value = snapshot.downloadedProjectId || "";
  downloadedProjectPath.value = snapshot.downloadedProjectPath || "";
  form.localFolder = downloadedProjectPath.value;

  if (snapshot.projectStructure) {
    applyProjectStructure(snapshot.projectStructure, snapshot.focusFilePath || "");
  } else {
    treeData.value = Array.isArray(snapshot.treeData) ? snapshot.treeData : [];
    treePathMap.value = snapshot.treePathMap && typeof snapshot.treePathMap === "object" ? snapshot.treePathMap : {};
  }

  selectedTreeNodeKey.value = snapshot.selectedTreeNodeKey || selectedTreeNodeKey.value;
  selectedTreePath.value = snapshot.selectedTreePath || selectedTreePath.value;
  form.focusFilePath = snapshot.focusFilePath || form.focusFilePath;
  form.codePreview = snapshot.codePreview || DEFAULT_CODE_PREVIEW;

  if (downloadedProjectId.value) {
    previewMode.value = "repo-file";
  }
  persistProjectSessionState();
};

onMounted(() => {
  restoreProjectSessionState();
  window.addEventListener("beforeunload", handleBeforeUnload);
  window.addEventListener("pagehide", handlePageHide);
});

onBeforeUnmount(() => {
  stopPolling();
  window.removeEventListener("beforeunload", handleBeforeUnload);
  window.removeEventListener("pagehide", handlePageHide);
  persistProjectSessionState();
});
</script>

<style scoped>
.code-review-workbench {
  height: 100%;
  min-height: 0;
}

.code-review-grid {
  height: 100%;
  min-height: 0;
  gap: 12px;
}

.code-review-operation {
  min-height: 0;
  height: 100%;
  min-width: 0;
}

.cr-task-card {
  width: 100%;
  height: 100%;
  min-width: 0;
  --wb-code-input-height: clamp(460px, calc(100vh - 360px), 680px);
}

.cr-task-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  min-height: 0;
  min-width: 0;
  width: 100%;
  max-width: none;
}

.cr-config-row {
  display: block;
  width: 100%;
  max-width: none;
  min-width: 0;
}

.cr-config-toolbar {
  display: flex !important;
  width: 100% !important;
  max-width: none !important;
  min-width: 0;
  gap: 16px;
  align-items: stretch;
  justify-content: flex-start !important;
  flex-wrap: nowrap !important;
  grid-template-columns: none !important;
}

.cr-config-toolbar :deep(.wb-config-chip) {
  min-height: 50px;
  align-items: center;
  justify-content: flex-start;
  gap: 12px;
}

.cr-upload-chip {
  flex: 0 0 220px;
  width: 220px;
  max-width: 220px;
}

.cr-repo-chip {
  flex: 1 1 auto;
  min-width: 0;
  max-width: none;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding-right: 8px;
}

.cr-model-chip {
  flex: 0 0 220px;
  width: 220px;
  max-width: 220px;
}

.cr-task-card .is-repo-chip {
  justify-content: flex-start;
}

.cr-task-card :deep(.cr-config-toolbar .wb-chip-upload),
.cr-task-card :deep(.cr-config-toolbar .wb-chip-control) {
  max-width: none !important;
  width: 100%;
  flex: 1 1 auto;
}

.cr-task-card :deep(.cr-config-toolbar .el-input),
.cr-task-card :deep(.cr-config-toolbar .el-select) {
  width: 100%;
}

.cr-repo-label {
  white-space: nowrap;
}

.cr-repo-input {
  min-width: 0;
}

.cr-repo-download.el-button {
  border-radius: 10px;
  border: 1px solid rgba(44, 107, 255, 0.24);
  background: rgba(44, 107, 255, 0.08);
  color: #2b5f98;
  padding: 6px 12px;
  min-width: 56px;
}

.cr-repo-download.el-button:hover {
  border-color: rgba(44, 107, 255, 0.36);
  background: rgba(44, 107, 255, 0.14);
}

.cr-code-editor {
  min-height: var(--wb-code-input-height);
  max-height: var(--wb-code-input-height);
}

.cr-code-caption {
  font-size: 12px;
  color: #5f7596;
}

.code-review-display {
  height: 100%;
  min-height: 0;
  grid-template-rows: minmax(240px, 0.8fr) minmax(360px, 1.2fr);
  gap: 12px;
}

.cr-tree-card,
.cr-result-card {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.cr-tree-card :deep(.el-card__body),
.cr-result-card :deep(.el-card__body) {
  min-height: 0;
  flex: 1;
  overflow: auto;
}

.cr-tree-scroll {
  min-height: 0;
  height: 100%;
  border: 1px solid rgba(44, 107, 255, 0.14);
  border-radius: 14px;
  background: #f7fbff;
  padding: 10px;
  overflow: auto;
}

.cr-tree-scroll :deep(.el-tree) {
  background: transparent;
  color: #28527f;
}

.cr-tree-scroll :deep(.el-tree-node__content) {
  height: 30px;
  border-radius: 8px;
}

.cr-tree-scroll :deep(.el-tree-node__content:hover) {
  background: rgba(44, 107, 255, 0.1);
}

.cr-overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.cr-overview-item {
  border: 1px solid rgba(44, 107, 255, 0.14);
  border-radius: 12px;
  background: #f7fbff;
  padding: 10px 12px;
  display: grid;
  gap: 5px;
}

.cr-overview-key {
  font-size: 12px;
  color: #5f7596;
}

.cr-overview-value {
  font-size: 13px;
  color: #234e82;
  line-height: 1.3;
  word-break: break-all;
}

.cr-list {
  margin: 0;
  padding-left: 20px;
  color: #28527f;
  font-size: 12px;
  line-height: 1.6;
}

.cr-empty {
  border: 1px dashed rgba(44, 107, 255, 0.22);
  border-radius: 12px;
  background: rgba(247, 251, 255, 0.9);
  color: #5f7596;
  font-size: 12px;
  padding: 10px 12px;
}

.cr-log-preview {
  min-height: 120px;
  max-height: 220px;
}

@media (max-width: 1200px) {
  .cr-config-toolbar {
    flex-wrap: wrap !important;
    gap: 12px;
  }

  .cr-upload-chip {
    flex: 0 0 220px;
    width: 220px;
    max-width: 220px;
  }

  .cr-model-chip {
    flex: 0 0 220px;
    width: 220px;
    max-width: 220px;
  }

  .cr-repo-chip {
    min-width: 0;
  }
}

@media (max-width: 1080px) {
  .code-review-grid {
    grid-template-columns: 1fr;
  }

  .code-review-display {
    grid-template-rows: auto;
  }

  .cr-task-card,
  .code-review-operation {
    height: auto;
    min-height: 0;
  }
}

@media (max-width: 720px) {
  .cr-config-toolbar {
    gap: 10px;
  }

  .cr-upload-chip,
  .cr-repo-chip,
  .cr-model-chip {
    flex: 1 1 100%;
    min-width: 0;
  }

  .cr-overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
