import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { AuthContext } from '../identity/useAuth'
import type { AuthState } from '../identity/useAuth'
import { productApi } from '../catalog/productApi'
import type { Product } from '../catalog/productApi'
import { customerApi } from '../customers/customerApi'
import { inventoryApi } from '../inventory/inventoryApi'
import { orderApi } from './orderApi'
import type { OrderDetail } from './orderApi'
import { ApiError } from '../../shared/api/http'

vi.mock('./orderApi', () => ({ orderApi: { list: vi.fn(), get: vi.fn(), create: vi.fn(), confirm: vi.fn() } }))
vi.mock('../catalog/productApi', () => ({ productApi: { list: vi.fn() } }))
vi.mock('../customers/customerApi', () => ({ customerApi: { list: vi.fn() } }))
vi.mock('../inventory/inventoryApi', () => ({ inventoryApi: { warehouses: vi.fn() } }))

const customerId = '11111111-1111-4111-8111-111111111111'
const warehouseId = '22222222-2222-4222-8222-222222222222'
const createdAt = '2026-11-06T12:00:00Z'
const product: Product = { id: '33333333-3333-4333-8333-333333333333', sku: 'BOLT-01', name: 'Steel bolt', description: null,
  category: 'Hardware', unitOfMeasure: 'Each', active: true, version: 0, createdAt, updatedAt: createdAt, sellingPrice: '12.50' }
const secondProduct: Product = { ...product, id: '44444444-4444-4444-8444-444444444444', sku: 'WASHER-01', name: 'Steel washer', sellingPrice: '0.10' }
const order: OrderDetail = { id: '55555555-5555-4555-8555-555555555555', requestId: '66666666-6666-4666-8666-666666666666', customerId, customerName: 'Fictional Hardware', warehouseId,
  warehouseName: 'Fictional warehouse', warehouseCode: 'WH-1', status: 'PENDING', currency: 'LKR', totalAmount: '25.30', createdAt, confirmedAt: null, createdBy: 'test-user', confirmedBy: null,
  items: [{ productId: product.id, sku: product.sku, productName: product.name, unitOfMeasure: 'Each', quantity: 2, unitPrice: '12.50', lineTotal: '25.00' },
    { productId: secondProduct.id, sku: secondProduct.sku, productName: secondProduct.name, unitOfMeasure: 'Each', quantity: 3, unitPrice: '0.10', lineTotal: '0.30' }] }
const confirmed: OrderDetail = { ...order, status: 'CONFIRMED', confirmedAt: createdAt, confirmedBy: 'test-user' }
const auth: AuthState = { user: { username: 'order-test', organizationName: 'Fictional Test Organization', canWriteCatalog: false, canWriteInventory: false,
  canWriteCustomers: false, canWritePrices: false, canWriteOrders: true }, loading: false, error: null, refresh: vi.fn(), signIn: vi.fn(), signOut: vi.fn() }
function page<T>(items: T[]) { return { items, totalElements: items.length, page: 0, size: 20, totalPages: items.length ? 1 : 0 } }
function open(path: string, state = auth) { render(<AuthContext.Provider value={state}><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthContext.Provider>) }

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(orderApi.list).mockResolvedValue(page([order]))
  vi.mocked(orderApi.get).mockResolvedValue(order)
  vi.mocked(orderApi.create).mockResolvedValue({ order, replayed: false })
  vi.mocked(orderApi.confirm).mockResolvedValue({ order: confirmed, replayed: false })
  vi.mocked(productApi.list).mockResolvedValue(page([product, secondProduct]))
  vi.mocked(customerApi.list).mockResolvedValue(page([{ id: customerId, name: order.customerName, contactName: null, email: null, phone: null, address: null, active: true, version: 0, createdAt, updatedAt: createdAt }]))
  vi.mocked(inventoryApi.warehouses).mockResolvedValue(page([{ id: warehouseId, code: 'WH-1', name: order.warehouseName, location: null, active: true, version: 0, createdAt, updatedAt: createdAt }]))
})

async function fillOrder() {
  const user = userEvent.setup()
  await screen.findByRole('option', { name: order.customerName })
  await user.selectOptions(screen.getByLabelText(/^Customer/), customerId)
  await user.selectOptions(screen.getByLabelText(/^Warehouse/), warehouseId)
  await user.selectOptions(screen.getByLabelText('Product to add'), product.id)
  await user.click(screen.getByRole('button', { name: 'Add product to order' }))
  await user.clear(screen.getByRole('spinbutton', { name: `Quantity for ${product.name}` }))
  await user.type(screen.getByRole('spinbutton', { name: `Quantity for ${product.name}` }), '2')
  return user
}

describe('order interactions', () => {
  it('loads and filters orders by search and status', async () => {
    const user = userEvent.setup()
    open('/orders')
    expect(screen.getByRole('status')).toHaveTextContent('Loading orders')
    await screen.findByRole('link', { name: `View order ${order.id}` })
    await user.type(screen.getByLabelText('Search orders'), 'Hardware')
    await user.selectOptions(screen.getByLabelText('Status'), 'CONFIRMED')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() => expect(orderApi.list).toHaveBeenLastCalledWith({ q: 'Hardware', status: 'CONFIRMED', page: 0, size: 20 }, expect.any(AbortSignal)))
  })

  it('recovers from a failed order list request and displays an honest empty state', async () => {
    vi.mocked(orderApi.list).mockRejectedValueOnce(new Error('Network')).mockResolvedValue(page([]))
    open('/orders')
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach TradeSphere')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('No orders found')).toBeInTheDocument()
  })

  it('creates a multi-item pending order without sending client prices or totals', async () => {
    open('/orders/new')
    const user = await fillOrder()
    await user.selectOptions(screen.getByLabelText('Product to add'), secondProduct.id)
    await user.click(screen.getByRole('button', { name: 'Add product to order' }))
    await user.clear(screen.getByRole('spinbutton', { name: `Quantity for ${secondProduct.name}` }))
    await user.type(screen.getByRole('spinbutton', { name: `Quantity for ${secondProduct.name}` }), '3')
    await user.click(screen.getByRole('button', { name: 'Create pending order' }))
    expect(await screen.findByText('Pending order created. No stock is reserved yet.')).toBeInTheDocument()
    expect(orderApi.create).toHaveBeenCalledWith({ requestId: expect.any(String), customerId, warehouseId,
      items: [{ productId: product.id, quantity: 2 }, { productId: secondProduct.id, quantity: 3 }] })
    expect(screen.getByText('LKR 25.30')).toBeInTheDocument()
  })

  it('rejects duplicate products and lets the employee remove an item', async () => {
    open('/orders/new')
    const user = await fillOrder()
    await user.selectOptions(screen.getByLabelText('Product to add'), product.id)
    await user.click(screen.getByRole('button', { name: 'Add product to order' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('already in the order')
    expect(screen.getAllByRole('spinbutton', { name: `Quantity for ${product.name}` })).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: `Remove ${product.name}` }))
    expect(screen.getByText('No products added yet.')).toBeInTheDocument()
  })

  it('rejects missing items and nonpositive quantities before sending the order', async () => {
    open('/orders/new')
    const user = await fillOrder()
    await user.clear(screen.getByRole('spinbutton', { name: `Quantity for ${product.name}` }))
    await user.type(screen.getByRole('spinbutton', { name: `Quantity for ${product.name}` }), '0')
    fireEvent.submit(screen.getByRole('form', { name: 'Create pending order' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Check the highlighted')
    expect(screen.getByRole('spinbutton', { name: `Quantity for ${product.name}` })).toHaveAttribute('aria-invalid', 'true')
    expect(orderApi.create).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: `Remove ${product.name}` }))
    fireEvent.submit(screen.getByRole('form', { name: 'Create pending order' }))
    expect(screen.getByText('Add at least one product.')).toBeInTheDocument()
  })

  it('keeps an uncertain creation locked and retries the same payload through a later error', async () => {
    vi.mocked(orderApi.create).mockRejectedValueOnce(new Error('Response lost'))
      .mockRejectedValueOnce(new ApiError({ status: 403, title: 'Denied', detail: 'Security token expired.' }))
      .mockResolvedValue({ order, replayed: true })
    open('/orders/new')
    const user = await fillOrder()
    await user.click(screen.getByRole('button', { name: 'Create pending order' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('could not confirm')
    expect(screen.getByRole('spinbutton', { name: `Quantity for ${product.name}` })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Retry same order' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Security token expired.')
    expect(screen.getByRole('spinbutton', { name: `Quantity for ${product.name}` })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Retry same order' }))
    expect(await screen.findByText('This order was already created. A duplicate was not added.')).toBeInTheDocument()
    const calls = vi.mocked(orderApi.create).mock.calls.map(([input]) => input)
    expect(calls[1]).toEqual(calls[0]); expect(calls[2]).toEqual(calls[0])
  })

  it('retains entered data on a known server validation rejection', async () => {
    vi.mocked(orderApi.create).mockRejectedValue(new ApiError({ status: 400, title: 'Invalid', detail: 'Invalid quantity.', errors: { 'items[0].quantity': 'Quantity is invalid.' } }))
    open('/orders/new')
    const user = await fillOrder()
    await user.click(screen.getByRole('button', { name: 'Create pending order' }))
    expect(await screen.findByText('Quantity is invalid.')).toBeInTheDocument()
    expect(screen.getByRole('spinbutton', { name: `Quantity for ${product.name}` })).toHaveValue(2)
    expect(screen.getByRole('spinbutton', { name: `Quantity for ${product.name}` })).not.toBeDisabled()
  })

  it('requires independent order permission for direct creation navigation', () => {
    open('/orders/new', { ...auth, user: { ...auth.user!, canWriteCatalog: true, canWriteInventory: true, canWriteOrders: false } })
    expect(screen.getByRole('alert')).toHaveTextContent('Order write permission is required')
    expect(orderApi.create).not.toHaveBeenCalled()
  })

  it('shows immutable snapshot amounts and confirms all items', async () => {
    open(`/orders/${order.id}`)
    await screen.findByRole('heading', { name: 'Order details' })
    const items = screen.getByRole('table', { name: 'Order items and snapshotted prices' })
    expect(within(items).getByText('LKR 12.50')).toBeInTheDocument()
    expect(within(items).getByText('LKR 0.10')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Confirm order' }))
    expect(await screen.findByText('Order confirmed. All items were reserved successfully.')).toBeInTheDocument()
    expect(screen.getByText('Confirmed', { exact: true, selector: 'span' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm order' })).not.toBeInTheDocument()
    expect(orderApi.confirm).toHaveBeenCalledWith(order.id)
  })

  it('keeps the order pending and displays an insufficient-stock conflict', async () => {
    vi.mocked(orderApi.confirm).mockRejectedValue(new ApiError({ status: 409, title: 'Conflict', detail: 'Insufficient stock for Steel washer.' }))
    open(`/orders/${order.id}`)
    await userEvent.click(await screen.findByRole('button', { name: 'Confirm order' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Insufficient stock')
    expect(screen.getByText('Pending', { exact: true })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm order' })).toBeEnabled()
  })

  it('retries uncertain confirmation for the same order without creating another', async () => {
    vi.mocked(orderApi.confirm).mockRejectedValueOnce(new Error('Response lost')).mockResolvedValue({ order: confirmed, replayed: true })
    open(`/orders/${order.id}`)
    await userEvent.click(await screen.findByRole('button', { name: 'Confirm order' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Confirmation may already have completed')
    await userEvent.click(screen.getByRole('button', { name: 'Retry confirmation' }))
    expect(await screen.findByText('This order was already confirmed. Stock was not reserved again.')).toBeInTheDocument()
    expect(orderApi.confirm).toHaveBeenNthCalledWith(1, order.id)
    expect(orderApi.confirm).toHaveBeenNthCalledWith(2, order.id)
    expect(orderApi.create).not.toHaveBeenCalled()
  })

  it('lets organization readers view orders without confirmation controls', async () => {
    open(`/orders/${order.id}`, { ...auth, user: { ...auth.user!, canWriteOrders: false } })
    expect(await screen.findByText('Order write permission is required to confirm this order.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm order' })).not.toBeInTheDocument()
  })
})
