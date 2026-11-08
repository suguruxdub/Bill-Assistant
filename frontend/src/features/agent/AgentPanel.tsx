import { useState } from 'react'
import { ApiError } from '../../shared/api/error'
import { chatWithAgent } from './api'
import './agent.css'

type Message = { role: 'user' | 'assistant'; text: string }

export default function AgentPanel() {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function submit(message = input) {
    const text = message.trim()
    if (!text || loading) return
    setInput('')
    setError('')
    setMessages(current => [...current, { role: 'user', text }])
    setLoading(true)
    try {
      const response = await chatWithAgent({ message: text })
      setMessages(current => [...current, { role: 'assistant', text: response.reply }])
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === 'AI_DISABLED') setError('AI 助手尚未配置模型服务。')
      else setError('AI 暂时无法回答，请稍后重试。')
    } finally {
      setLoading(false)
    }
  }

  return <section className="agent-panel" aria-labelledby="agent-title">
    <div className="agent-heading"><div><p className="eyebrow">AI ASSISTANT</p><h2 id="agent-title">问问你的账单</h2></div><span className="agent-badge">只读分析</span></div>
    {messages.length === 0 && <div className="agent-suggestions"><button type="button" onClick={() => submit('帮我统计一下本月消费')}>统计本月消费</button><button type="button" onClick={() => submit('今年消费最多的分类是什么？')}>找出消费最多分类</button></div>}
    {messages.length > 0 && <div className="agent-messages" aria-live="polite">{messages.map((message, index) => <p className={`agent-message ${message.role}`} key={`${message.role}-${index}`}>{message.text}</p>)}{loading && <p className="agent-message assistant">正在分析账单…</p>}</div>}
    {error && <p className="form-error" role="alert">{error}</p>}
    <form className="agent-form" onSubmit={event => { event.preventDefault(); void submit() }}><input value={input} maxLength={2000} onChange={event => setInput(event.target.value)} placeholder="例如：上个月餐饮花了多少钱？" aria-label="向 AI 助手提问" disabled={loading} /><button className="primary-button" type="submit" disabled={loading || !input.trim()}>{loading ? '分析中…' : '发送'}</button></form>
  </section>
}
