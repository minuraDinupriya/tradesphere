import { request } from '../../shared/api/http'

export interface ProductValues {
  sku: string; name: string; description: string | null; category: string; unitOfMeasure: string; active: boolean
}
export interface Product extends ProductValues { id: string; version: number; createdAt: string; updatedAt: string; sellingPrice: string | null }
export interface ProductPage { items: Product[]; totalElements: number; page: number; size: number; totalPages: number }
export interface ProductFilters { q: string; category: string; active: string; page: number; size: number }

export const productApi = {
  list(filters: ProductFilters, signal?: AbortSignal) {
    const params = new URLSearchParams({ q: filters.q, category: filters.category,
      page: String(filters.page), size: String(filters.size) })
    if (filters.active) params.set('active', filters.active)
    return request<ProductPage>(`/products?${params}`, { signal })
  },
  categories: (signal?: AbortSignal) => request<string[]>('/products/categories', { signal }),
  get: (id: string, signal?: AbortSignal) => request<Product>(`/products/${encodeURIComponent(id)}`, { signal }),
  create: (values: ProductValues) => request<Product>('/products', { method: 'POST', body: JSON.stringify(values) }),
  update: (id: string, values: ProductValues, version: number) => request<Product>(`/products/${encodeURIComponent(id)}`,
    { method: 'PUT', body: JSON.stringify({ ...values, version }) }),
}
