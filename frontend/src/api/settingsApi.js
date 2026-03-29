const API_BASE = import.meta.env.VITE_API_BASE || "";
const SETTINGS_API_PATH = "/api/settings/siliconflow-key";

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
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

export function fetchSiliconFlowKeyStatus() {
  return request(`${SETTINGS_API_PATH}?_t=${Date.now()}`, {
    cache: "no-store"
  });
}

export function saveSiliconFlowKey(apiKey) {
  return request(SETTINGS_API_PATH, {
    method: "PUT",
    cache: "no-store",
    body: JSON.stringify({ apiKey })
  });
}
