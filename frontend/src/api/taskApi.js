const API_BASE = import.meta.env.VITE_API_BASE || "";

async function request(path) {
  const response = await fetch(`${API_BASE}${path}`);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export function fetchTaskList(limit = 20) {
  return request(`/api/tasks?limit=${limit}`);
}
