import { ApiError } from './error'

const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

export function send(path: string, options: RequestInit = {}): Promise<Response> {
  return fetch(`${baseUrl}${path}`, {
    ...options,
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', 'X-Requested-With': 'BillAssistant', ...options.headers },
  })
}

export async function readError(response: Response): Promise<ApiError> {
  const data = await response.json().catch(() => ({})) as { message?: unknown; code?: unknown }
  return new ApiError(response.status,
    typeof data.message === 'string' ? data.message : '服务暂时不可用，请稍后重试。',
    typeof data.code === 'string' ? data.code : '')
}

export async function decode<T>(response: Response): Promise<T> {
  if (!response.ok) throw await readError(response)
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

