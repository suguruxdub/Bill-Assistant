import { ApiError } from './error'

export type AppTokens = {
  tokenType: 'Bearer'
  accessToken: string
  refreshToken: string
  expiresIn: number
  refreshExpiresIn: number
}

export interface AppTokenStore {
  load(): Promise<AppTokens | null>
  save(tokens: AppTokens): Promise<void>
  clear(): Promise<void>
}

/**
 * The default store is intentionally process-local. Native apps should inject
 * a secure-storage implementation (for example Keychain/Keystore) instead.
 */
export class MemoryAppTokenStore implements AppTokenStore {
  private tokens: AppTokens | null = null

  load(): Promise<AppTokens | null> {
    return Promise.resolve(this.tokens)
  }

  save(tokens: AppTokens): Promise<void> {
    this.tokens = tokens
    return Promise.resolve()
  }

  clear(): Promise<void> {
    this.tokens = null
    return Promise.resolve()
  }
}

type AppAuthResponse<T> = { user: T; tokens: AppTokens }
type ErrorBody = { message?: unknown; code?: unknown }

export type AppClientOptions = {
  /** API root, for example `/api` in a browser or `http://10.0.2.2:8080/api` on Android. */
  apiBaseUrl?: string
  tokenStore?: AppTokenStore
  fetchImpl?: typeof fetch
}

/** Native-client API with Bearer authentication and one refresh-and-retry. */
export class AppApiClient {
  private readonly apiBaseUrl: string
  private readonly tokenStore: AppTokenStore
  private readonly fetchImpl: typeof fetch
  private readonly ready: Promise<void>
  private tokens: AppTokens | null = null
  private refreshPromise: Promise<void> | null = null

  constructor(options: AppClientOptions = {}) {
    this.apiBaseUrl = (options.apiBaseUrl ?? import.meta.env.VITE_APP_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL ?? '/api').replace(/\/$/, '')
    this.tokenStore = options.tokenStore ?? new MemoryAppTokenStore()
    this.fetchImpl = options.fetchImpl ?? fetch
    this.ready = this.tokenStore.load().then(tokens => { this.tokens = tokens })
  }

  async register<T>(data: unknown): Promise<T> {
    return this.authenticate<T>('/app/auth/register', data)
  }

  async login<T>(username: string, password: string): Promise<T> {
    return this.authenticate<T>('/app/auth/login', { username, password })
  }

  async currentUser<T>(signal?: AbortSignal): Promise<T> {
    return this.request<T>('/app/auth/me', { signal })
  }

  async logout(): Promise<void> {
    await this.ready
    const refreshToken = this.tokens?.refreshToken
    try {
      if (refreshToken) {
        await this.fetchJson<void>('/app/auth/logout', {
          method: 'POST',
          body: JSON.stringify({ refreshToken }),
        })
      }
    } finally {
      this.tokens = null
      await this.tokenStore.clear()
    }
  }

  private async authenticate<T>(path: string, body: unknown): Promise<T> {
    const response = await this.fetchJson<AppAuthResponse<T>>(path, {
      method: 'POST',
      body: JSON.stringify(body),
    })
    await this.saveTokens(response.tokens)
    return response.user
  }

  async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    await this.ready
    const response = await this.fetchRaw(path, options)
    if (response.status !== 401) return this.decode<T>(response)
    const error = await this.readError(response)
    if (error.code !== 'ACCESS_TOKEN_INVALID') throw error
    await this.refresh()
    return this.decode<T>(await this.fetchRaw(path, options))
  }

  private async refresh(): Promise<void> {
    if (!this.refreshPromise) {
      this.refreshPromise = (async () => {
        await this.ready
        const refreshToken = this.tokens?.refreshToken
        if (!refreshToken) throw new ApiError(401, '登录已失效，请重新登录', 'REFRESH_TOKEN_INVALID')
        const response = await this.fetchJson<AppTokens>('/app/auth/refresh', {
          method: 'POST',
          body: JSON.stringify({ refreshToken }),
        })
        await this.saveTokens(response)
      })().finally(() => { this.refreshPromise = null })
    }
    return this.refreshPromise
  }

  private async saveTokens(tokens: AppTokens): Promise<void> {
    if (tokens.tokenType !== 'Bearer' || !tokens.accessToken || !tokens.refreshToken) {
      throw new ApiError(502, '登录服务返回了无效的令牌', 'AUTH_RESPONSE_INVALID')
    }
    this.tokens = tokens
    await this.tokenStore.save(tokens)
  }

  private async fetchRaw(path: string, options: RequestInit): Promise<Response> {
    const headers = new Headers(options.headers)
    headers.set('Content-Type', 'application/json')
    headers.set('X-Requested-With', 'BillAssistant')
    if (this.tokens?.accessToken) headers.set('Authorization', `Bearer ${this.tokens.accessToken}`)
    return this.fetchImpl(`${this.apiBaseUrl}${path}`, {
      ...options,
      credentials: 'omit',
      headers,
    })
  }

  private async fetchJson<T>(path: string, options: RequestInit): Promise<T> {
    const response = await this.fetchImpl(`${this.apiBaseUrl}${path}`, {
      ...options,
      credentials: 'omit',
      headers: {
        'Content-Type': 'application/json',
        'X-Requested-With': 'BillAssistant',
        ...options.headers,
      },
    })
    return this.decode<T>(response)
  }

  private async decode<T>(response: Response): Promise<T> {
    if (!response.ok) throw await this.readError(response)
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  }

  private async readError(response: Response): Promise<ApiError> {
    const data = await response.json().catch(() => ({})) as ErrorBody
    return new ApiError(response.status,
      typeof data.message === 'string' ? data.message : '服务暂时不可用，请稍后重试。',
      typeof data.code === 'string' ? data.code : '')
  }
}

let sharedAppClient = new AppApiClient()

/** Configure the bundled Capacitor UI with a native API URL and secure token store. */
export function configureAppClient(options: AppClientOptions): AppApiClient {
  sharedAppClient = new AppApiClient(options)
  return sharedAppClient
}

export function getAppClient(): AppApiClient {
  return sharedAppClient
}

