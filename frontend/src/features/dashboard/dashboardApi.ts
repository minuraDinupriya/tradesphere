import { request } from '../../shared/api/http'
import type { OrderStatus } from '../ordering/orderApi'

export interface DashboardSummary {
  counts: { activeProducts: number; warehouses: number; activeCustomers: number; recordedBalances: number; zeroAvailableBalances: number; totalOrders: number }
  ordersByStatus: { status: OrderStatus; total: number }[]
  recentOrders: { id: string; customerName: string; status: OrderStatus; createdAt: string }[]
}
export const dashboardApi = { get: (signal?: AbortSignal) => request<DashboardSummary>('/dashboard', { signal }) }
