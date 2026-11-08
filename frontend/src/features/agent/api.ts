import { Capacitor } from '@capacitor/core'
import { getAppClient } from '../../shared/api/app-session'
import { request } from '../../shared/api/client'
import type { AgentChatRequest, AgentChatResponse } from './types'

export function chatWithAgent(data: AgentChatRequest, signal?: AbortSignal): Promise<AgentChatResponse> {
  const options: RequestInit = { method: 'POST', body: JSON.stringify(data), signal }
  if (Capacitor.isNativePlatform()) return getAppClient().request<AgentChatResponse>('/agent/chat', options)
  return request<AgentChatResponse>('/agent/chat', options)
}
