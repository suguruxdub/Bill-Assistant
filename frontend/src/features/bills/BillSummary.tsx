import type { Bill } from './types'

type Props = { bills: Bill[]; loading: boolean; error: string }

export default function BillSummary({ bills, loading, error }: Props) {
  const total = bills.reduce((sum, bill) => sum + bill.amount, 0)
  const unpaid = bills.filter(bill => !bill.paid).length

  return (
    <section className="summary-grid" aria-label="账单概览">
      <article className="summary-card accent">
        <span>账单总金额</span>
        <strong>{loading || error ? '—' : new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(total)}</strong>
        <small>基于当前账户的账单记录</small>
      </article>
      <article className="summary-card">
        <span>账单数量</span>
        <strong>{loading || error ? '—' : bills.length}</strong>
        <small>包含已付与待付项目</small>
      </article>
      <article className="summary-card">
        <span>待支付</span>
        <strong>{loading || error ? '—' : unpaid}</strong>
        <small>及时处理，避免遗漏</small>
      </article>
    </section>
  )
}
