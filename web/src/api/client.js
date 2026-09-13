const BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(message, status = 500) {
    super(message)
    this.status = status
  }
}

async function readJson(response) {
  return response.json().catch(() => null)
}

async function request(path, options, attempt = 1) {
  let response
  try {
    response = await fetch(`${BASE}${path}`, {
      credentials: 'include',
      ...options,
    })
  } catch {
    if (attempt === 1) {
      await new Promise((resolve) => setTimeout(resolve, 700))
      return request(path, options, 2)
    }
    throw new ApiError('Cannot reach the server. Wait a moment and try again.')
  }

  const json = await readJson(response)
  if (!response.ok || !json?.success) {
    throw new ApiError(json?.message || 'Request failed', response.status)
  }
  return json
}

export async function api(path, { method = 'GET', body, token } = {}) {
  return request(path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export function apiBaseUrl() {
  return BASE
}

export async function apiUpload(path, { token, fields = {}, file, fileField = 'screenshot' } = {}) {
  const form = new FormData()
  Object.entries(fields).forEach(([key, value]) => {
    if (value !== undefined && value !== null) form.append(key, String(value))
  })
  if (file) form.append(fileField, file)

  return request(path, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  })
}
