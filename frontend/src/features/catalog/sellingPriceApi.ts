import { request } from '../../shared/api/http'

export interface SellingPrice { productId: string; currency: 'LKR'; amount: string | null; version: number }

export const sellingPriceApi = {
  get: (id: string, signal?: AbortSignal) => request<SellingPrice>(`/products/${encodeURIComponent(id)}/selling-price`, { signal }),
  update: (id: string, amount: string, version: number) => request<SellingPrice>(`/products/${encodeURIComponent(id)}/selling-price`,
    { method: 'PUT', body: JSON.stringify({ amount, version }) }),
}

/** Format the API decimal string without passing money through JavaScript floating-point arithmetic. */
export function formatLkr(amount: string | null | undefined) {
  if (amount == null) return 'Not set'
  const [whole, fraction = ''] = amount.split('.')
  return `LKR ${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction.padEnd(2, '0')}`
}
