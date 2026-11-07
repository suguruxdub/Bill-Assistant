import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../shared/api/error'
import { fetchCurrentUser } from '../features/auth/api'
import type { UserProfile } from '../features/auth/types'
import AuthPage from '../features/auth/AuthPage'
import Dashboard from '../features/bills/Dashboard'

export default function App() {
  const [user, setUser] = useState<UserProfile | null>(null)
  const [checking, setChecking] = useState(true)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const clearUser = useCallback(() => setUser(null), [])

  useEffect(() => {
    const syncAccount = () => {
      setUser(null)
      setChecking(true)
      setAttempt(value => value + 1)
    }
    window.addEventListener('bill-assistant-auth-changed', syncAccount)
    return () => window.removeEventListener('bill-assistant-auth-changed', syncAccount)
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setChecking(true)
    setError('')
    fetchCurrentUser(controller.signal)
      .then(setUser)
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return
        if (cause instanceof ApiError && cause.status === 401) setUser(null)
        else setError('无法连接服务，请确认后端已启动。')
      })
      .finally(() => {
        if (!controller.signal.aborted) setChecking(false)
      })
    return () => controller.abort()
  }, [attempt])

  if (checking) return <main className="session-screen"><p role="status">正在恢复登录状态…</p></main>
  if (error) return <main className="session-screen"><p role="alert">{error}</p><button className="primary-button" onClick={() => setAttempt(attempt + 1)}>重新连接</button></main>
  if (!user) return <AuthPage onAuthenticated={setUser} />
  return <Dashboard key={user.id} user={user} onSignedOut={clearUser} />
}
