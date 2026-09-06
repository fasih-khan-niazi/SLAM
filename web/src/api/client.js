const BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(message, status = 500) {
    super(message)
    this.status = status
  }
}

export async function api(path, { method = 'GET', body, token } = {}) {
  let response
  try {
    response = await fetch(`${BASE}${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError('Cannot reach the API. Confirm it is running and VITE_API_BASE_URL is correct.')
  }

  const json = await response.json().catch(() => null)
  if (!response.ok || !json?.success) {
    throw new ApiError(json?.message || 'Request failed', response.status)
  }
  return json
}

export function apiBaseUrl() {
  return BASE
}
