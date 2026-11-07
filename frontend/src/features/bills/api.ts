import { Capacitor } from '@capacitor/core'
import { getAppClient } from '../../shared/api/app-session'
import { request } from '../../shared/api/client'
import type { Bill, CreateBillRequest } from './types'

const native = Capacitor.isNativePlatform()

export function fetchBills(signal?: AbortSignal): Promise<Bill[]> {
  if (native) return getAppClient().request<Bill[]>('/bills', { signal })
  return request<Bill[]>('/bills', { signal })
}

export function createBill(data: CreateBillRequest, signal?: AbortSignal): Promise<Bill> {
  if (native) return getAppClient().request<Bill>('/bills', { method: 'POST', body: JSON.stringify(data), signal })
  return request<Bill>('/bills', { method: 'POST', body: JSON.stringify(data), signal })
}

