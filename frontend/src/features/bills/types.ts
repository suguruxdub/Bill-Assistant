export type Bill = {
  id: number
  title: string
  amount: number
  billingDate: string
  category: string
  paid: boolean
}

export type CreateBillRequest = Omit<Bill, 'id'>

