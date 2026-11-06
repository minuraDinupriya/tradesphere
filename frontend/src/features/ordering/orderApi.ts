import { request } from '../../shared/api/http'

export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'PROCESSING' | 'FULFILLED' | 'CANCELLED'
export type OrderAction = 'confirm' | 'process' | 'cancel' | 'fulfill'
export interface OrderStateEvent { id: string; fromStatus: OrderStatus | null; toStatus: OrderStatus; actorId: string; actorUsername: string; occurredAt: string; backfilled: boolean }
export interface OrderMovement { id: string; orderId: string; warehouseId: string; productId: string; sku: string; productName: string; kind: 'RELEASE' | 'FULFILL'; quantity: number; onHandDelta: number; reservedDelta: number; actorId: string; actorUsername: string; occurredAt: string }
export interface OrderHistory { transitions: OrderStateEvent[]; movements: OrderMovement[] }
export const orderStatusLabels: Record<OrderStatus, string> = { PENDING: 'Pending', CONFIRMED: 'Confirmed', PROCESSING: 'Processing', FULFILLED: 'Fulfilled', CANCELLED: 'Cancelled' }
export const orderActionLabels: Record<OrderAction, string> = { confirm: 'Confirm order', process: 'Start processing', cancel: 'Cancel order', fulfill: 'Fulfill order' }
export interface OrderSummary {
  id: string
  requestId: string
  customerId: string
  customerName: string
  warehouseId: string
  warehouseName: string
  warehouseCode: string
  status: OrderStatus
  currency: 'LKR'
  totalAmount: string
  createdAt: string
  confirmedAt: string | null
  createdBy: string
  confirmedBy: string | null
}
export interface OrderItem {
  productId: string
  sku: string
  productName: string
  unitOfMeasure: string
  quantity: number
  unitPrice: string
  lineTotal: string
}
export interface OrderDetail extends OrderSummary { items: OrderItem[] }
export interface OrderInput { requestId: string; customerId: string; warehouseId: string; items: { productId: string; quantity: number }[] }
export interface OrderResult { order: OrderDetail; replayed: boolean }
export interface OrderFilters { q: string; status: string; page: number; size: number }
export interface OrderPage { items: OrderSummary[]; totalElements: number; page: number; size: number; totalPages: number }

export const orderApi = {
  list(filters: OrderFilters, signal?: AbortSignal) {
    const parameters = new URLSearchParams({ q: filters.q, page: String(filters.page), size: String(filters.size) })
    if (filters.status) parameters.set('status', filters.status)
    return request<OrderPage>(`/orders?${parameters}`, { signal })
  },
  get: (id: string, signal?: AbortSignal) => request<OrderDetail>(`/orders/${encodeURIComponent(id)}`, { signal }),
  create: (input: OrderInput) => request<OrderResult>('/orders', { method: 'POST', body: JSON.stringify(input) }),
  confirm: (id: string) => request<OrderResult>(`/orders/${encodeURIComponent(id)}/confirm`, { method: 'POST' }),
  act: (id: string, action: OrderAction) => request<OrderResult>(`/orders/${encodeURIComponent(id)}/${action}`, { method: 'POST' }),
  history: (id: string, signal?: AbortSignal) => request<OrderHistory>(`/orders/${encodeURIComponent(id)}/history`, { signal }),
}
