import { request } from '../../shared/api/http'

export interface CustomerValues {
  name: string
  contactName: string | null
  email: string | null
  phone: string | null
  address: string | null
  active: boolean
}
export interface Customer extends CustomerValues { id: string; version: number; createdAt: string; updatedAt: string }
export interface CustomerFilters { q: string; active: string; page: number; size: number }
export interface CustomerPage { items: Customer[]; totalElements: number; page: number; size: number; totalPages: number }

export const customerApi = {
  list(filters: CustomerFilters, signal?: AbortSignal) {
    const parameters = new URLSearchParams({ q: filters.q, page: String(filters.page), size: String(filters.size) })
    if (filters.active) parameters.set('active', filters.active)
    return request<CustomerPage>(`/customers?${parameters}`, { signal })
  },
  get: (id: string, signal?: AbortSignal) => request<Customer>(`/customers/${encodeURIComponent(id)}`, { signal }),
  create: (values: CustomerValues) => request<Customer>('/customers', { method: 'POST', body: JSON.stringify(values) }),
  update: (id: string, values: CustomerValues, version: number) => request<Customer>(`/customers/${encodeURIComponent(id)}`,
    { method: 'PUT', body: JSON.stringify({ ...values, version }) }),
}
