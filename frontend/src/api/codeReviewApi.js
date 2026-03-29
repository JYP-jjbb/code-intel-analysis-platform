export const API_BASE = import.meta.env.VITE_API_BASE || "";

function resolveApiUrl(path) {
  const normalizedPath = String(path || "").startsWith("/")
    ? String(path || "")
    : `/${String(path || "")}`;
  const trimmedBase = String(API_BASE || "").trim().replace(/\/+$/, "");

  if (!trimmedBase) {
    return normalizedPath;
  }

  // Prevent duplicated "/api" when API_BASE is ".../api" and path starts with "/api/...".
  if (/\/api$/i.test(trimmedBase) && normalizedPath.startsWith("/api/")) {
    return `${trimmedBase}${normalizedPath.slice(4)}`;
  }
  return `${trimmedBase}${normalizedPath}`;
}

async function request(path, options = {}) {
  const response = await fetch(resolveApiUrl(path), {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });

  const rawText = await response.text();
  let data = null;
  if (rawText) {
    try {
      data = JSON.parse(rawText);
    } catch (_) {
      data = null;
    }
  }

  if (!response.ok) {
    let message = `Request failed: ${response.status}`;
    if (data && typeof data === "object") {
      message = data.message || data.error || message;
    } else if (rawText) {
      message = rawText;
    }

    const error = new Error(message);
    if (data && typeof data === "object") {
      if (data.detail) error.detail = String(data.detail);
      if (Array.isArray(data.hints)) error.hints = data.hints.map((item) => String(item || "").trim()).filter(Boolean);
      if (data.errorCode) error.errorCode = String(data.errorCode);
      error.httpStatus = response.status;
    }
    throw error;
  }
  if (data && typeof data === "object") {
    return data;
  }
  return {};
}

export function submitCodeReviewTask(payload) {
  return request("/api/tasks/code-review", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function fetchTaskDetail(taskId) {
  return request(`/api/tasks/${taskId}`);
}

export function fetchTaskLogs(taskId) {
  return request(`/api/tasks/${taskId}/logs`);
}

export function fetchCodeReviewResult(taskId) {
  return request(`/api/tasks/${taskId}/code-review-result`);
}

export function downloadCodeReviewRepository(payload) {
  return request("/api/code-review/projects/download", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function fetchCodeReviewProjectFile({ pageSessionId, projectId, path }) {
  const query = new URLSearchParams({
    pageSessionId: String(pageSessionId || ""),
    path: String(path || "")
  });
  return request(`/api/code-review/projects/${encodeURIComponent(projectId || "")}/file?${query.toString()}`);
}

export function cleanupCodeReviewProject(payload, { keepalive = false } = {}) {
  return fetch(resolveApiUrl("/api/code-review/projects/cleanup"), {
    method: "POST",
    keepalive,
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload || {})
  }).then(async (response) => {
    if (!response.ok) {
      const text = await response.text();
      throw new Error(text || `Request failed: ${response.status}`);
    }
    return response.json();
  });
}
