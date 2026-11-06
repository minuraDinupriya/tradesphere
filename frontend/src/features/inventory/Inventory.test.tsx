import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { AuthContext } from '../identity/useAuth'
import type { AuthState } from '../identity/useAuth'
import { productApi } from '../catalog/productApi'
import { ApiError } from '../../shared/api/http'
import { inventoryApi } from './inventoryApi'
import type { InventoryBalance, StockMovement, Warehouse } from './inventoryApi'

vi.mock('./inventoryApi', () => ({ inventoryApi: { warehouses: vi.fn(), warehouse: vi.fn(), createWarehouse: vi.fn(), updateWarehouse: vi.fn(), balances: vi.fn(), movements: vi.fn(), receive: vi.fn() } }))
vi.mock('../catalog/productApi', () => ({ productApi: { list: vi.fn(), categories: vi.fn(), get: vi.fn() } }))

const warehouse: Warehouse = { id: '11111111-1111-4111-8111-111111111111', code: 'COL-01', name: 'Colombo test warehouse', location: 'Fictional location', active: true, version: 0, createdAt: '2026-11-06T12:00:00Z', updatedAt: '2026-11-06T12:00:00Z' }
const product = { id: '22222222-2222-4222-8222-222222222222', name: 'Packing tape', sku: 'TAPE-01', description: null, category: 'Packaging', unitOfMeasure: 'Each', active: true, version: 0, createdAt: warehouse.createdAt, updatedAt: warehouse.updatedAt, sellingPrice: null }
const balance: InventoryBalance = { warehouseId: warehouse.id, warehouseCode: warehouse.code, warehouseName: warehouse.name, productId: product.id, productName: product.name, sku: product.sku, unitOfMeasure: 'Each', onHand: 25, reserved: 6, available: 19, warehouseActive: true, productActive: true, updatedAt: warehouse.updatedAt }
const movement: StockMovement = { id: '33333333-3333-4333-8333-333333333333', requestId: '44444444-4444-4444-8444-444444444444', warehouseId: warehouse.id, warehouseCode: warehouse.code, warehouseName: warehouse.name, productId: product.id, productName: product.name, sku: product.sku, unitOfMeasure: 'Each', quantity: 25, reference: 'DEL-15', note: null, receivedBy: 'test-user-id', receivedByUsername: 'test-operator', receivedAt: warehouse.createdAt }
const auth: AuthState = { user: { username: 'test-operator', organizationName: 'Fictional Organization', canWriteCatalog: false, canWriteInventory: true, canWriteCustomers: false, canWritePrices: false }, loading: false, error: null, refresh: vi.fn(), signIn: vi.fn(), signOut: vi.fn() }
function page<T>(item: T) { return { items: [item], page: 0, size: 20, totalElements: 1, totalPages: 1 } }
function open(path: string, state = auth) { return render(<AuthContext.Provider value={state}><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthContext.Provider>) }

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(inventoryApi.warehouses).mockResolvedValue(page(warehouse))
  vi.mocked(inventoryApi.warehouse).mockResolvedValue(warehouse)
  vi.mocked(inventoryApi.createWarehouse).mockResolvedValue(warehouse)
  vi.mocked(inventoryApi.updateWarehouse).mockResolvedValue({ ...warehouse, version: 1 })
  vi.mocked(inventoryApi.balances).mockResolvedValue(page(balance))
  vi.mocked(inventoryApi.movements).mockResolvedValue(page(movement))
  vi.mocked(inventoryApi.receive).mockResolvedValue({ movement, replayed: false })
  vi.mocked(productApi.list).mockResolvedValue(page(product))
})

describe('warehouse and inventory interactions', () => {
  it('creates a warehouse and displays success', async () => {
    const user = userEvent.setup(); open('/warehouses/new')
    await user.type(screen.getByLabelText(/Warehouse name/), warehouse.name)
    await user.type(screen.getByLabelText(/Warehouse code/), 'col-01')
    await user.click(screen.getByRole('button', { name: 'Save warehouse' }))
    expect(await screen.findByText('Warehouse created successfully.')).toBeInTheDocument()
    expect(inventoryApi.createWarehouse).toHaveBeenCalledWith({ code: 'col-01', name: warehouse.name, location: null, active: true })
  })

  it('links server warehouse field errors to the actual inputs', async () => {
    vi.mocked(inventoryApi.createWarehouse).mockRejectedValue(new ApiError({ status: 400, title: 'Validation failed', detail: 'Check the fields.', errors: { code: 'Invalid warehouse code.' } }))
    const user = userEvent.setup(); open('/warehouses/new')
    await user.type(screen.getByLabelText(/Warehouse name/), 'Test warehouse')
    await user.type(screen.getByLabelText(/Warehouse code/), 'COL-01')
    await user.click(screen.getByRole('button', { name: 'Save warehouse' }))
    expect(await screen.findByText('Invalid warehouse code.')).toBeInTheDocument()
    expect(screen.getByLabelText(/Warehouse code/)).toHaveAttribute('aria-describedby', 'warehouse-code-error')
    expect(screen.getByLabelText(/Warehouse code/)).toHaveAttribute('aria-invalid', 'true')
  })

  it('filters warehouses and displays inactive status', async () => {
    const user = userEvent.setup(); open('/warehouses')
    await screen.findByRole('link', { name: warehouse.name })
    await user.type(screen.getByLabelText('Search warehouses'), 'Colombo')
    await user.selectOptions(screen.getByLabelText('Status'), 'false')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() => expect(inventoryApi.warehouses).toHaveBeenLastCalledWith({ q: 'Colombo', active: false, page: 0, size: 20 }, expect.any(AbortSignal)))
  })

  it('shows real on-hand, reserved and computed available quantities', async () => {
    open('/inventory')
    expect(screen.getByRole('status')).toHaveTextContent('Loading inventory')
    expect(await screen.findByRole('cell', { name: '25' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '6' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: '19' })).toBeInTheDocument()
    expect(screen.getByText(/Available = on-hand/)).toBeInTheDocument()
  })

  it('recovers from inventory failures and shows an honest empty state', async () => {
    vi.mocked(inventoryApi.balances).mockRejectedValueOnce(new Error('Network error')).mockResolvedValue({ ...page(balance), items: [], totalElements: 0, totalPages: 0 })
    open('/inventory')
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach TradeSphere')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('No stock recorded')).toBeInTheDocument()
  })

  it('receives stock using selected warehouse/product and whole-unit quantity', async () => {
    const user = userEvent.setup(); open('/inventory/receive')
    await screen.findByRole('option', { name: `${warehouse.name} (${warehouse.code})` })
    await user.selectOptions(screen.getByLabelText(/^Warehouse/), warehouse.id)
    await user.selectOptions(screen.getByLabelText(/^Product/), product.id)
    await user.type(screen.getByLabelText(/Quantity received/), '25')
    await user.type(screen.getByLabelText('Reference'), 'DEL-15')
    await user.click(screen.getByRole('button', { name: 'Receive stock' }))
    expect(await screen.findByText('Stock received successfully.')).toBeInTheDocument()
    expect(inventoryApi.receive).toHaveBeenCalledWith(expect.objectContaining({ warehouseId: warehouse.id, productId: product.id, quantity: 25, reference: 'DEL-15', note: null, requestId: expect.any(String) }))
  })

  it('keeps an uncertain receipt locked and retries the identical request', async () => {
    vi.mocked(inventoryApi.receive).mockRejectedValueOnce(new Error('Response lost')).mockResolvedValue({ movement, replayed: true })
    const user = userEvent.setup(); open('/inventory/receive')
    await screen.findByRole('option', { name: `${warehouse.name} (${warehouse.code})` })
    await user.selectOptions(screen.getByLabelText(/^Warehouse/), warehouse.id)
    await user.selectOptions(screen.getByLabelText(/^Product/), product.id)
    await user.type(screen.getByLabelText(/Quantity received/), '25')
    await user.click(screen.getByRole('button', { name: 'Receive stock' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('could not confirm')
    expect(screen.getByLabelText(/Quantity received/)).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Retry same receipt' }))
    expect(await screen.findByText('This receipt was already recorded. Stock was not increased again.')).toBeInTheDocument()
    expect(vi.mocked(inventoryApi.receive).mock.calls[1][0]).toEqual(vi.mocked(inventoryApi.receive).mock.calls[0][0])
  })

  it('catalog write permission alone does not expose inventory mutation controls', async () => {
    open('/warehouses', { ...auth, user: { ...auth.user!, canWriteCatalog: true, canWriteInventory: false } })
    await screen.findByRole('link', { name: warehouse.name })
    expect(screen.queryByRole('link', { name: /Add warehouse/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: `Edit ${warehouse.name}` })).not.toBeInTheDocument()
  })

  it('preserves an uncertain receipt through a later permission error', async () => {
    vi.mocked(inventoryApi.receive)
      .mockRejectedValueOnce(new Error('Response lost'))
      .mockRejectedValueOnce(new ApiError({ status: 403, title: 'Access denied', detail: 'Security token expired.' }))
      .mockResolvedValue({ movement, replayed: true })
    const user = userEvent.setup()
    open('/inventory/receive')
    await screen.findByRole('option', { name: `${warehouse.name} (${warehouse.code})` })
    await user.selectOptions(screen.getByLabelText(/^Warehouse/), warehouse.id)
    await user.selectOptions(screen.getByLabelText(/^Product/), product.id)
    await user.type(screen.getByLabelText(/Quantity received/), '25')
    await user.click(screen.getByRole('button', { name: 'Receive stock' }))
    await user.click(await screen.findByRole('button', { name: 'Retry same receipt' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Security token expired.')
    expect(screen.getByLabelText(/Quantity received/)).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Retry same receipt' }))
    expect(await screen.findByText('This receipt was already recorded. Stock was not increased again.')).toBeInTheDocument()
    const requests = vi.mocked(inventoryApi.receive).mock.calls.map(([input]) => input)
    expect(requests[1]).toEqual(requests[0])
    expect(requests[2]).toEqual(requests[0])
  })

  it('rejects direct stock-receiving navigation without inventory permission', () => {
    open('/inventory/receive', { ...auth, user: { ...auth.user!, canWriteInventory: false } })
    expect(screen.getByRole('alert')).toHaveTextContent('Inventory write permission is required')
    expect(inventoryApi.receive).not.toHaveBeenCalled()
  })

  it('shows the stock movement reference and recorded employee', async () => {
    open('/inventory/movements')
    expect(await screen.findByRole('cell', { name: 'DEL-15' })).toBeInTheDocument()
    expect(screen.getByRole('cell', { name: 'test-operator' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View receipt DEL-15' })).toBeInTheDocument()
  })
})
