import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { ApiError } from '../../shared/api/error'
import { createBill } from './api'
import type { Bill } from './types'

type Props = {
  onClose: () => void
  onCreated: (bill: Bill) => void
  onSignedOut: () => void
}

export default function CreateBillDialog({ onClose, onCreated, onSignedOut }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const requestRef = useRef<AbortController | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const dialog = dialogRef.current!
    dialog.showModal()
    dialog.querySelector<HTMLInputElement>('[name="title"]')?.focus()
    return () => {
      requestRef.current?.abort()
      dialog.close()
    }
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (requestRef.current) return
    const data = new FormData(event.currentTarget)
    const title = String(data.get('title') ?? '').trim()
    const category = String(data.get('category') ?? '').trim()
    const amountText = String(data.get('amount') ?? '')
    if (!title || !category) {
      setError('请填写账单名称和分类，不能只输入空格。')
      return
    }
    if (!/^\d{1,12}(\.\d{1,2})?$/.test(amountText) || Number(amountText) < 0.01) {
      setError('金额应在 0.01～999999999999.99 之间，最多保留两位小数。')
      return
    }

    const controller = new AbortController()
    requestRef.current = controller
    setSaving(true)
    setError('')
    try {
      const bill = await createBill({
        title,
        amount: Number(amountText),
        billingDate: String(data.get('billingDate')),
        category,
        paid: data.get('paid') === 'true',
      }, controller.signal)
      if (!controller.signal.aborted) onCreated(bill)
    } catch (cause) {
      if (controller.signal.aborted) return
      if (cause instanceof ApiError && cause.status === 401) onSignedOut()
      else setError(cause instanceof ApiError ? cause.message : '无法保存账单，请检查网络后重试。')
    } finally {
      requestRef.current = null
      if (!controller.signal.aborted) setSaving(false)
    }
  }

  return (
    <dialog ref={dialogRef} className="create-bill-dialog" aria-labelledby="create-bill-title"
      onCancel={event => { event.preventDefault(); if (!saving) onClose() }}>
      <div className="section-heading">
        <div>
          <p className="eyebrow">NEW BILL</p>
          <h2 id="create-bill-title">创建账单</h2>
        </div>
        <button className="text-button" type="button" disabled={saving} onClick={onClose} aria-label="关闭创建账单">关闭</button>
      </div>
      <p className="bill-form-description">记下这笔支出，账单会保存到你的账户。</p>
      <form onSubmit={handleSubmit}>
        <fieldset disabled={saving}>
          <label>账单名称<input name="title" required maxLength={255} placeholder="例如：午餐、房租、水电费" autoFocus /></label>
          <div className="bill-form-row">
            <label>金额（元）<input name="amount" type="number" inputMode="decimal" required min="0.01" max="999999999999.99" step="0.01" placeholder="0.00" /></label>
            <label>账单日期<input name="billingDate" type="date" required defaultValue={new Date().toLocaleDateString('sv-SE')} /></label>
          </div>
          <div className="bill-form-row">
            <label>分类<input name="category" required maxLength={64} placeholder="例如：餐饮" list="bill-category-options" /></label>
            <datalist id="bill-category-options">
              {['餐饮', '交通', '购物', '居住', '医疗', '娱乐', '其他'].map(category => <option key={category} value={category} />)}
            </datalist>
            <label>支付状态<select name="paid" defaultValue="true"><option value="true">已支付</option><option value="false">待支付</option></select></label>
          </div>
        </fieldset>
        {error && <p className="form-error" role="alert">{error}</p>}
        <div className="bill-form-actions">
          <button className="text-button" type="button" disabled={saving} onClick={onClose}>取消</button>
          <button className="primary-button" type="submit" disabled={saving}>{saving ? '正在保存…' : '保存账单'}</button>
        </div>
      </form>
    </dialog>
  )
}
