import { request } from '../../shared/api/http'

export interface PageResult<T> { items: T[]; totalElements: number; page: number; size: number; totalPages: number }
export interface WarehouseValues { code: string; name: string; location: string | null; active: boolean }
export interface Warehouse extends WarehouseValues { id: string; version: number; createdAt: string; updatedAt: string }
export interface InventoryBalance {
  warehouseId: string; warehouseCode: string; warehouseName: string; productId: string; sku: string; productName: string;
  unitOfMeasure: string; onHand: number; reserved: number; available: number; warehouseActive: boolean; productActive: boolean; updatedAt: string
}
export interface StockReceiptInput { requestId: string; warehouseId: string; productId: string; quantity: number; reference: string | null; note: string | null }
export interface StockMovement extends Omit<StockReceiptInput, 'note'> {
  id: string; note: string | null; warehouseCode: string; warehouseName: string; sku: string; productName: string; unitOfMeasure: string;
  receivedBy: string; receivedByUsername: string; receivedAt: string
}
export interface InventoryFilters { q: string; warehouseId: string; page: number; size: number }

function params(values: Record<string, string | number | boolean | undefined>) {
  const result = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => { if (value !== undefined && value !== '') result.set(key, String(value)) })
  return result.toString()
}

export const inventoryApi = {
  warehouses: (filters: { q?: string; active?: boolean; page?: number; size?: number }, signal?: AbortSignal) =>
    request<PageResult<Warehouse>>(`/warehouses?${params(filters)}`, { signal }),
  warehouse: (id: string, signal?: AbortSignal) => request<Warehouse>(`/warehouses/${encodeURIComponent(id)}`, { signal }),
  createWarehouse: (values: WarehouseValues) => request<Warehouse>('/warehouses', { method: 'POST', body: JSON.stringify(values) }),
  updateWarehouse: (id: string, values: WarehouseValues, version: number) =>
    request<Warehouse>(`/warehouses/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify({ ...values, version }) }),
  balances: (filters: InventoryFilters, signal?: AbortSignal) => request<PageResult<InventoryBalance>>(`/inventory?${params({ ...filters })}`, { signal }),
  movements: (filters: InventoryFilters, signal?: AbortSignal) => request<PageResult<StockMovement>>(`/inventory/movements?${params({ ...filters })}`, { signal }),
  receive: (input: StockReceiptInput) => request<{ movement: StockMovement; replayed: boolean }>('/inventory/receipts', { method: 'POST', body: JSON.stringify(input) }),
}
