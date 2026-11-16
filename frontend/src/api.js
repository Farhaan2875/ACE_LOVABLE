// All communication with the Spring Boot backend lives in this file.

const TOKEN_KEY = 'ace_token'

export const getToken = () => localStorage.getItem(TOKEN_KEY)
export const setToken = (token) => (token ? localStorage.setItem(TOKEN_KEY, token) : localStorage.removeItem(TOKEN_KEY))

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

// Turns a failed HTTP response into an ApiError carrying the backend's "message" field.
async function toError(response) {
  let message = response.statusText || 'Request failed'
  try {
    const body = await response.json()
    if (body.errors?.length) message = body.errors.map((e) => `${e.field}: ${e.message}`).join(', ')
    else if (body.message) message = body.message
  } catch {
    /* body was not JSON */
  }
  if (response.status === 401 && getToken()) {
    setToken(null) // token expired -> back to login
    window.location.href = '/login'
  }
  return new ApiError(response.status, message)
}

async function request(path, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (getToken()) headers.Authorization = `Bearer ${getToken()}`

  const response = await fetch(`/api${path}`, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  if (!response.ok) throw await toError(response)
  if (response.status === 204) return null
  const type = response.headers.get('content-type') || ''
  return type.includes('json') ? response.json() : response.text()
}

export const api = {
  // auth
  login: (username, password) => request('/auth/login', { method: 'POST', body: { username, password } }),
  signup: (username, name, password) => request('/auth/signup', { method: 'POST', body: { username, name, password } }),
  me: () => request('/auth/me'),

  // projects
  projects: () => request('/projects'),
  project: (id) => request(`/projects/${id}`),
  createProject: (name) => request('/projects', { method: 'POST', body: { name } }),
  renameProject: (id, name) => request(`/projects/${id}`, { method: 'PATCH', body: { name } }),
  deleteProject: (id) => request(`/projects/${id}`, { method: 'DELETE' }),

  // files
  files: (id) => request(`/projects/${id}/files`),
  file: (id, path) => request(`/projects/${id}/files/${path.split('/').map(encodeURIComponent).join('/')}`),

  // members
  members: (id) => request(`/projects/${id}/members`),
  invite: (id, username, role) => request(`/projects/${id}/members`, { method: 'POST', body: { username, role } }),
  setRole: (id, userId, role) => request(`/projects/${id}/members/${userId}`, { method: 'PATCH', body: { role } }),
  removeMember: (id, userId) => request(`/projects/${id}/members/${userId}`, { method: 'DELETE' }),

  // chat history + preview
  messages: (id) => request(`/projects/${id}/chat/messages`),
  preview: (id) => request(`/projects/${id}/preview`),
  startPreview: (id) => request(`/projects/${id}/preview`, { method: 'POST' }),
  stopPreview: (id) => request(`/projects/${id}/preview`, { method: 'DELETE' }),
  previewLogs: (id) => request(`/projects/${id}/preview/logs`),

  // billing + usage
  plans: () => request('/plans'),
  subscription: () => request('/me/subscription'),
  usage: () => request('/usage/today'),
  checkout: (planId) => request('/stripe/checkout', { method: 'POST', body: { planId } }),
  portal: () => request('/stripe/portal', { method: 'POST' }),
}

/**
 * Sends a prompt and reads the answer as Server-Sent Events.
 * EventSource cannot send a POST body or an Authorization header, so we read the stream with fetch().
 * onEvent(name, data) is called for: start, text, tool, file_start, file_delta, file_end, file_error, warning, error, done
 */
export async function streamChat(projectId, message, onEvent, signal) {
  const response = await fetch(`/api/projects/${projectId}/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${getToken()}`, Accept: 'text/event-stream' },
    body: JSON.stringify({ message }),
    signal,
  })
  if (!response.ok) throw await toError(response)

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // one event = "event: name\ndata: {json}" followed by an empty line
    let end
    while ((end = buffer.indexOf('\n\n')) >= 0) {
      const frame = buffer.slice(0, end)
      buffer = buffer.slice(end + 2)
      let name = 'message'
      let data = ''
      for (const line of frame.split('\n')) {
        if (line.startsWith('event:')) name = line.slice(6).trim()
        else if (line.startsWith('data:')) data += line.slice(5).trim()
      }
      if (data) onEvent(name, JSON.parse(data))
    }
  }
}
