const STORAGE_KEY = "code-review-project-page-session-v1";

const asString = (value, fallback = "") => {
  if (value === null || value === undefined) {
    return fallback;
  }
  return String(value);
};

const sanitizeSnapshot = (snapshot = {}) => ({
  pageSessionId: asString(snapshot.pageSessionId),
  repoUrl: asString(snapshot.repoUrl),
  downloadedProjectId: asString(snapshot.downloadedProjectId),
  downloadedProjectPath: asString(snapshot.downloadedProjectPath),
  model: asString(snapshot.model),
  projectStructure: asString(snapshot.projectStructure),
  focusFilePath: asString(snapshot.focusFilePath),
  selectedTreeNodeKey: asString(snapshot.selectedTreeNodeKey),
  selectedTreePath: asString(snapshot.selectedTreePath),
  codePreview: asString(snapshot.codePreview),
  treeData: Array.isArray(snapshot.treeData) ? snapshot.treeData : [],
  treePathMap: snapshot.treePathMap && typeof snapshot.treePathMap === "object" ? snapshot.treePathMap : {}
});

export const generatePageSessionId = () => {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  const rand = Math.random().toString(36).slice(2, 12);
  return `cr-${Date.now().toString(36)}-${rand}`;
};

export const loadCodeReviewProjectSnapshot = () => {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    return sanitizeSnapshot(JSON.parse(raw));
  } catch (_) {
    return null;
  }
};

export const saveCodeReviewProjectSnapshot = (snapshot) => {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(sanitizeSnapshot(snapshot)));
  } catch (_) {
    // ignore
  }
};

export const clearCodeReviewProjectSnapshot = () => {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch (_) {
    // ignore
  }
};
