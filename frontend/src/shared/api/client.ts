import { ApiError } from './error'
import { decode, readError, send } from './http'
import { assertAuthGeneration, getAuthGeneration, refreshAccess } from './auth-session'

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const generation = getAuthGeneration()
  const response = await send(path, options)
  assertAuthGeneration(generation)
  if (response.status !== 401) return decode<T>(response)
  const error = await readError(response)
  if (error.code !== 'ACCESS_TOKEN_INVALID') throw error
  await refreshAccess(generation)
  options.signal?.throwIfAborted()
  assertAuthGeneration(generation)
  // Only an interceptor rejection is retried; business operations are never replayed after arbitrary errors.
  const retried = await send(path, options)
  assertAuthGeneration(generation)
  return decode<T>(retried)
}

