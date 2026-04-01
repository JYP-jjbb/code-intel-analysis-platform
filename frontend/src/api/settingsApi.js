const API_BASE = import.meta.env.VITE_API_BASE || "";
const PROVIDER_SETTINGS_API_PREFIX = "/api/settings/llm/providers";
const KEY_ENDPOINTS = {
  kimi: "/api/settings/moonshot-key",
  moonshot: "/api/settings/moonshot-key",
  deepseek: "/api/settings/deepseek-key",
  hunyuan: "/api/settings/hunyuan-key",
  qwen: "/api/settings/qwen-key"
};

function resolveApiUrl(path) {
  const normalizedPath = String(path || "").startsWith("/")
    ? String(path || "")
    : `/${String(path || "")}`;
  const trimmedBase = String(API_BASE || "").replace(/\/+$/, "");
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
    } catch (_) {
      data = null;
    }
  }

  if (!response.ok) {
    let message = `Request failed: ${response.status}`;
    if (data) {
      message = data.message || data.error || message;
    } else if (rawText) {
      message = rawText;
    }
    throw new Error(message);
  }

  if (data) {
    return data;
  }
  return {};
}

function normalizeProviderId(providerId) {
  const key = String(providerId || "").trim().toLowerCase();
  if (key === "moonshot") {
    return "kimi";
  }
  return key;
}

export function fetchProviderSettings(providerId) {
  const key = normalizeProviderId(providerId);
  return request(`${PROVIDER_SETTINGS_API_PREFIX}/${encodeURIComponent(key)}?_t=${Date.now()}`, {
    cache: "no-store"
  });
}

export function saveProviderSettings(providerId, payload) {
  const key = normalizeProviderId(providerId);
  return request(`${PROVIDER_SETTINGS_API_PREFIX}/${encodeURIComponent(key)}`, {
    method: "PUT",
    cache: "no-store",
    body: JSON.stringify(payload || {})
  });
}

function fetchApiKeyStatusByPath(path) {
  return request(`${path}?_t=${Date.now()}`, { cache: "no-store" });
}

function saveApiKeyByPath(path, apiKey) {
  return request(path, {
    method: "PUT",
    cache: "no-store",
    body: JSON.stringify({ apiKey })
  });
}

export function fetchMoonshotKeyStatus() {
  return fetchApiKeyStatusByPath(KEY_ENDPOINTS.kimi);
}

export function saveMoonshotKey(apiKey) {
  return saveApiKeyByPath(KEY_ENDPOINTS.kimi, apiKey);
}

export function fetchHunyuanKeyStatus() {
  return fetchApiKeyStatusByPath(KEY_ENDPOINTS.hunyuan);
}

export function saveHunyuanKey(apiKey) {
  return saveApiKeyByPath(KEY_ENDPOINTS.hunyuan, apiKey);
}

export function fetchDeepSeekKeyStatus() {
  return fetchApiKeyStatusByPath(KEY_ENDPOINTS.deepseek);
}

export function saveDeepSeekKey(apiKey) {
  return saveApiKeyByPath(KEY_ENDPOINTS.deepseek, apiKey);
}

export function fetchQwenKeyStatus() {
  return fetchApiKeyStatusByPath(KEY_ENDPOINTS.qwen);
}

export function saveQwenKey(apiKey) {
  return saveApiKeyByPath(KEY_ENDPOINTS.qwen, apiKey);
}
