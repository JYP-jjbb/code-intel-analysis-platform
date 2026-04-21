const API_BASE = import.meta.env.VITE_API_BASE || "";

function resolveApiUrl(path) {
  const normalizedPath = String(path || "").startsWith("/")
    ? String(path || "")
    : `/${String(path || "")}`;
  const trimmedBase = String(API_BASE || "").trim().replace(/\/+$/, "");
  if (!trimmedBase) {
    return normalizedPath;
  }
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
    } catch (parseError) {
      data = null;
    }
  }

  if (!response.ok) {
    let message = `Request failed: ${response.status}`;
    if (data) {
      if (data.message && data.message !== "No message available") {
        message = data.message;
      } else if (data.error && data.path) {
        message = `${data.error}: ${data.path}`;
      } else {
        message = data.error || data.message || message;
      }
    } else if (rawText) {
      message = rawText;
    }

    if (message === "No message available" && data && data.path) {
      message = `接口不可用: ${data.path}，请确认后端已更新并重启`;
    }
    throw new Error(message);
  }

  if (data) {
    return data;
  }
  return {};
}

export function generateRankingFunction(payload, requestOptions = {}) {
  return request("/api/nutera/generate-ranking-function", {
    method: "POST",
    body: JSON.stringify(payload),
    ...requestOptions
  });
}

export function explainLearningCode(payload) {
  return request("/api/nutera/learning/explain-code", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function buildVerificationSummaryGraph(payload) {
  return request("/api/nutera/verification/summary-graph", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function startBatchTask(payload) {
  return request("/api/nutera/batch/start", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function fetchBatchStatus(taskId) {
  const params = new URLSearchParams({ taskId: String(taskId || "") });
  return request(`/api/nutera/batch/status?${params.toString()}`);
}

export function pauseBatchTask(taskId) {
  return request("/api/nutera/batch/pause", {
    method: "POST",
    body: JSON.stringify({ taskId: String(taskId || "") })
  });
}

export function fetchBatchReports(limit = 50) {
  const params = new URLSearchParams({ limit: String(limit) });
  return request(`/api/nutera/reports?${params.toString()}`);
}

export function fetchBatchReportDetail(taskId) {
  return request(`/api/nutera/reports/${encodeURIComponent(String(taskId || ""))}`);
}

export function fetchBatchReportCases(taskId) {
  return request(`/api/nutera/reports/${encodeURIComponent(String(taskId || ""))}/cases`);
}

export async function exportBatchReportCsv(taskId) {
  const response = await fetch(resolveApiUrl(`/api/nutera/reports/${encodeURIComponent(String(taskId || ""))}/export`), {
    method: "GET"
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  const disposition = response.headers.get("Content-Disposition") || "";
  const matched = disposition.match(/filename=\"?([^\";]+)\"?/i);
  const filename = matched?.[1] || `report-${taskId || "cases"}.csv`;
  const blob = await response.blob();
  return { blob, filename };
}

export function deleteBatchReport(taskId) {
  return request(`/api/nutera/reports/${encodeURIComponent(String(taskId || ""))}`, {
    method: "DELETE"
  });
}

export function loadCaseSource(dataset, entryIndex = 0) {
  const params = new URLSearchParams({
    dataset,
    entryIndex: String(entryIndex)
  });
  return request(`/api/nutera/case-source?${params.toString()}`);
}

export function submitNuteraTask(payload) {
  return request("/api/tasks/nutera", {
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

export function fetchTaskResult(taskId) {
  return request(`/api/tasks/${taskId}/result`);
}

