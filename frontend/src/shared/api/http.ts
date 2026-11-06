export interface Problem {
  status: number
  title: string
  detail: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  readonly problem: Problem
  constructor(problem: Problem) {
    super(problem.detail || problem.title)
    this.name = 'ApiError'
    this.problem = problem
  }
}

let csrf: { headerName: string; token: string } | null = null

export function resetCsrf() { csrf = null }

async function csrfToken() {
  if (!csrf) {
    const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
    if (!response.ok) throw new Error('Could not establish a secure session. Please reload and try again.')
    csrf = await response.json() as { headerName: string; token: string }
  }
  return csrf!
}

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  if (options.body && !(options.body instanceof URLSearchParams)) headers.set('Content-Type', 'application/json')
  if (options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method)) {
    const token = await csrfToken()
    headers.set(token.headerName, token.token)
  }
  const response = await fetch(`/api/v1${path}`, { ...options, headers, credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) {
    const body = await response.json().catch(() => null) as Partial<Problem> | null
    throw new ApiError({ status: response.status, title: body?.title ?? 'Request failed',
      detail: body?.detail ?? 'The request could not be completed. Please try again.', errors: body?.errors })
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}

export function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : 'Could not reach TradeSphere. Check your connection and try again.'
}
