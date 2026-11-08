import { useEffect, useState } from 'react'
import { ApiError } from '../../shared/api/error'
import { logout } from '../auth/api'
import ProfileCard from '../auth/ProfileCard'
import { fetchBills } from './api'
import BillSummary from './BillSummary'
import BillList from './BillList'
import CreateBillDialog from './CreateBillDialog'
import './bills.css'
import type { Bill } from './types'
import type { UserProfile } from '../auth/types'
import AgentPanel from '../agent/AgentPanel'

type Props = { user: UserProfile; onSignedOut: () => void }

export default function Dashboard({ user, onSignedOut }: Props) {
  const [bills, setBills] = useState<Bill[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [signingOut, setSigningOut] = useState(false)
  const [logoutError, setLogoutError] = useState('')
  const [showProfile, setShowProfile] = useState(false)
  const [creatingBill, setCreatingBill] = useState(false)
  const [success, setSuccess] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    fetchBills(controller.signal)
      .then(setBills)
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return
        if (cause instanceof ApiError && cause.status === 401) onSignedOut()
        else setError('无法加载账单，请确认后端服务已启动并刷新页面。')
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [onSignedOut])

  async function handleLogout() {
    setSigningOut(true)
    setLogoutError('')
    try {
      await logout()
      onSignedOut()
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) onSignedOut()
      else setLogoutError('退出失败，请检查网络后重试。')
    } finally {
      setSigningOut(false)
    }
  }

  function handleCreated(bill: Bill) {
    setBills(current => [...current, bill].sort((a, b) =>
      b.billingDate.localeCompare(a.billingDate) || b.id - a.id))
    setCreatingBill(false)
    setSuccess('账单已创建。')
  }

  return (
    <main className="shell">
      <nav className="account-bar" aria-label="账户操作">
        <span className="account-name"><span className="user-avatar" aria-hidden="true">{user.nickname.slice(0, 1)}</span>{user.nickname}</span>
        <div><button className="text-button" type="button" aria-expanded={showProfile} onClick={() => setShowProfile(!showProfile)}>个人信息</button><button className="text-button" type="button" disabled={signingOut} onClick={handleLogout}>{signingOut ? '正在退出…' : '退出登录'}</button></div>
      </nav>
      {logoutError && <p className="form-error" role="alert">{logoutError}</p>}
      {showProfile && <ProfileCard user={user} />}
      <header className="header">
        <div>
          <p className="eyebrow">BILL ASSISTANT</p>
          <h1>掌握每一笔日常支出</h1>
          <p className="subtitle">清晰记录，轻松规划你的每月生活。</p>
        </div>
        <button className="primary-button create-bill-button" type="button" disabled={loading || signingOut || !!error}
          onClick={() => { setSuccess(''); setCreatingBill(true) }}>＋ 创建账单</button>
      </header>

      {error && <p className="notice" role="alert">{error}</p>}
      {success && <p className="bill-success" role="status">{success}</p>}

      <BillSummary bills={bills} loading={loading} error={error} />
      <BillList bills={bills} loading={loading} error={error} />
      <AgentPanel />
      {creatingBill && <CreateBillDialog onClose={() => setCreatingBill(false)} onCreated={handleCreated} onSignedOut={onSignedOut} />}
    </main>
  )
}
