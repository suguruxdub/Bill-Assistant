import { Capacitor } from '@capacitor/core'
import { getAppClient } from '../../shared/api/app-session'
import { request } from '../../shared/api/client'
import { authenticate, logout as webLogout } from '../../shared/api/auth-session'
import type { RegisterRequest, UserProfile } from './types'

const native = Capacitor.isNativePlatform()

export function fetchCurrentUser(signal?: AbortSignal): Promise<UserProfile> {
  if (native) return getAppClient().currentUser<UserProfile>(signal)
  return request<UserProfile>('/auth/me', { signal })
}

export function login(username: string, password: string): Promise<UserProfile> {
  if (native) return getAppClient().login<UserProfile>(username, password)
  return authenticate('/auth/login', { username, password })
}

export function register(data: RegisterRequest): Promise<UserProfile> {
  if (native) return getAppClient().register<UserProfile>(data)
  return authenticate('/auth/register', data)
}

export function logout(): Promise<void> {
  return native ? getAppClient().logout() : webLogout()
}

