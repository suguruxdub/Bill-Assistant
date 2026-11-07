import type { Bill } from './types'

function formatAmount(amount: number) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(amount)
}

type Props = { bills: Bill[]; loading: boolean; error: string }

export default function BillList({ bills, loading, error }: Props) {
  return (
    <section className="list-card">
      <div className="section-heading">
        <div>
          <p className="eyebrow">BILLS</p>
          <h2>我的账单</h2>
        </div>
      </div>
      <div className="bill-list">
        {bills.map((bill) => (
          <div className="bill-row" key={bill.id}>
            <div className="bill-icon">{bill.category.slice(0, 1)}</div>
            <div className="bill-info">
              <strong>{bill.title}</strong>
              <span>{bill.category} · {bill.billingDate}</span>
            </div>
            <span className={`status ${bill.paid ? 'paid' : 'pending'}`}>{bill.paid ? '已支付' : '待支付'}</span>
            <strong className="bill-amount">{formatAmount(bill.amount)}</strong>
          </div>
        ))}
        {loading && <p className="empty" role="status">正在加载账单…</p>}
        {!loading && !bills.length && !error && <p className="empty">暂无账单</p>}
      </div>
    </section>
  )
}
