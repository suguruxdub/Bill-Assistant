import { ApiError } from './error'
import { decode, readError, send } from './http'

let refreshPromise: Promise<void> | null = null
let authGeneration = 0
const authChannel = typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel('bill-assistant-auth')
authChannel?.addEventListener('message', () => {
  authGeneration += 1
  window.dispatchEvent(new Event('bill-assistant-auth-changed'))
})

function authChanged() {
  authGeneration += 1
  authChannel?.postMessage('changed')
}

async function authLock<T>(action: () => Promise<T>): Promise<T> {
  if (navigator.locks) return navigator.locks.request('bill-assistant-auth', action)
  return action()
}

export function assertAuthGeneration(generation: number) {
  if (generation !== authGeneration) throw new ApiError(401, '账户状态已变化，请重新加载页面。', 'AUTH_CHANGED')
}

export function refreshAccess(generation: number): Promise<void> {
  if (!refreshPromise) {
    refreshPromise = authLock(async () => {
      assertAuthGeneration(generation)
      // A different request or tab may already have refreshed while this one waited.
      const current = await send('/auth/me')
      if (current.ok) return
      const currentError = await readError(current)
      if (currentError.code !== 'ACCESS_TOKEN_INVALID') throw currentError
      const refreshed = await send('/auth/refresh', { method: 'POST' })
      if (refreshed.ok) return
      const error = await readError(refreshed)
      if (error.code === 'REFRESH_CONFLICT') {
        // A concurrent response may still be delivering the replacement cookies.
        for (let attempt = 0; attempt < 10; attempt += 1) {
          await new Promise(resolve => setTimeout(resolve, 100))
          const check = await send('/auth/me')
          if (check.ok) return
          if (check.status !== 401) throw await readError(check)
        }
      }
      throw error
    }).finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

export async function authenticate<T>(path: string, body: unknown): Promise<T> {
  return authLock(async () => {
    const user = await decode<T>(await send(path, { method: 'POST', body: JSON.stringify(body) }))
    authChanged()
    return user
  })
}

export function logout(): Promise<void> {
  // Serialized with refresh in this tab and other tabs; logout also works without a valid access token.
  authChanged()
  return authLock(async () => {
    await decode<void>(await send('/auth/logout', { method: 'POST' }))
    authChanged()
  })
}

export function getAuthGeneration(): number {
  return authGeneration
}
