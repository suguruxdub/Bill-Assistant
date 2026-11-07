import { useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/error'
import { login, register } from './api'
import type { UserProfile } from './types'
import './auth.css'

type Props = { onAuthenticated: (user: UserProfile) => void }

export default function AuthPage({ onAuthenticated }: Props) {
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const registering = mode === 'register'

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const value = (key: string) => String(data.get(key) ?? '')
    const optional = (key: string) => value(key).trim() || null
    if (registering && value('password') !== value('confirmPassword')) {
      setError('两次输入的密码不一致。')
      return
    }
    setError('')
    setBusy(true)
    try {
      const user = registering
        ? await register({
          username: value('username'), password: value('password'), nickname: optional('nickname'),
          email: optional('email'), phone: optional('phone'),
          gender: value('gender') as UserProfile['gender'], birthday: optional('birthday'),
        })
        : await login(value('username'), value('password'))
      onAuthenticated(user)
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : '无法连接服务，请确认后端已启动。')
    } finally {
      setBusy(false)
    }
  }

  function switchMode(next: 'login' | 'register') {
    setMode(next)
    setError('')
  }

  return (
    <main className="auth-shell">
      <section className="auth-intro">
        <p className="eyebrow">BILL ASSISTANT</p>
        <h1>把生活的账，<br />记得明明白白。</h1>
        <p className="subtitle">记录每一笔支出，留更多心思给生活。</p>
        <div className="intro-note"><span aria-hidden="true">¥</span><p>你的账单，只属于你。<br /><small>登录后随时查看自己的收支记录。</small></p></div>
      </section>
      <section className="auth-card" aria-label={registering ? '用户注册' : '用户登录'}>
        <div className="auth-tabs" aria-label="账户入口">
          <button type="button" disabled={busy} aria-pressed={!registering} onClick={() => switchMode('login')}>登录</button>
          <button type="button" disabled={busy} aria-pressed={registering} onClick={() => switchMode('register')}>注册</button>
        </div>
        <h2>{registering ? '创建你的账户' : '欢迎回来'}</h2>
        <p className="form-description">{registering ? '注册后即可开始管理个人账单。' : '登录账户，继续查看你的账单。'}</p>
        <form key={mode} onSubmit={handleSubmit}>
          <fieldset disabled={busy}>
            <label>用户名<input name="username" required minLength={registering ? 3 : 1} maxLength={32}
              pattern={registering ? '[a-zA-Z0-9][a-zA-Z0-9_]{2,31}' : undefined}
              autoComplete="username" placeholder="输入用户名" autoCapitalize="none" spellCheck={false} /></label>
            {registering && <p className="field-hint">3～32 位字母、数字或下划线，且不能以下划线开头；不区分大小写。</p>}
            <label>密码<input name="password" type="password" required minLength={registering ? 8 : 1} maxLength={72}
              autoComplete={registering ? 'new-password' : 'current-password'} placeholder={registering ? '至少 8 个字符' : '输入密码'} /></label>
            {registering && <>
              <label>确认密码<input name="confirmPassword" type="password" required minLength={8} maxLength={72} autoComplete="new-password" placeholder="再次输入密码" /></label>
              <details className="optional-fields">
                <summary>完善个人信息（可选）</summary>
                <label>昵称<input name="nickname" maxLength={64} autoComplete="nickname" placeholder="希望我们如何称呼你" /></label>
                <label>邮箱<input name="email" type="email" maxLength={254} autoComplete="email" placeholder="name@example.com" /></label>
                <label>联系电话<input name="phone" type="tel" maxLength={32} autoComplete="tel" placeholder="输入联系电话" /></label>
                <div className="form-row">
                  <label>性别<select name="gender" defaultValue="UNSPECIFIED"><option value="UNSPECIFIED">不设置</option><option value="MALE">男</option><option value="FEMALE">女</option><option value="OTHER">其他</option></select></label>
                  <label>生日<input name="birthday" type="date" autoComplete="bday" max={new Date().toLocaleDateString('sv-SE')} /></label>
                </div>
              </details>
            </>}
          </fieldset>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button submit-button" type="submit" disabled={busy}>{busy ? '正在处理…' : registering ? '注册并登录' : '登录'}</button>
        </form>
        <p className="auth-footer">{registering ? '已有账户？' : '还没有账户？'} <button className="text-button" type="button" disabled={busy} onClick={() => switchMode(registering ? 'login' : 'register')}>{registering ? '去登录' : '立即注册'}</button></p>
      </section>
    </main>
  )
}
