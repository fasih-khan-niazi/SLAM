const BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000').replace(/\/$/, '')

const DEVICE_KEY = 'slam_device_id'

/** A random id for this browser, sent with every request so sign-in limits count per device. */
function deviceId() {
  try {
    let id = localStorage.getItem(DEVICE_KEY)
    if (!id) {
      id = (crypto.randomUUID && crypto.randomUUID()) ||
        `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
      localStorage.setItem(DEVICE_KEY, id)
    }
    return id
  } catch {
    return ''
  }
}

let unauthorizedHandler = null

/** Called when the server rejects a request that carried a sign-in token (expired, replaced, revoked). */
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

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
  const device = deviceId()
  try {
    response = await fetch(`${BASE}${path}`, {
      credentials: 'include',
      ...options,
      headers: { ...(options.headers || {}), ...(device ? { 'X-Device-Id': device } : {}) },
    })
  } catch {
    if (attempt === 1) {
      await new Promise((resolve) => setTimeout(resolve, 700))
      return request(path, options, 2)
    }
    throw new ApiError('Cannot reach the server. Wait a moment and try again.')
  }

  const json = await readJson(response)
  const sentToken = Boolean(options.headers && options.headers.Authorization)
  if (response.status === 401 && sentToken && unauthorizedHandler) {
    unauthorizedHandler()
  }
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
