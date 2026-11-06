import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../App'
import { AuthContext } from '../identity/useAuth'
import type { AuthState } from '../identity/useAuth'
import { customerApi } from './customerApi'
import type { Customer } from './customerApi'
import { productApi } from '../catalog/productApi'
import { formatLkr, sellingPriceApi } from '../catalog/sellingPriceApi'
import { ApiError } from '../../shared/api/http'

vi.mock('./customerApi', () => ({ customerApi: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn() } }))
vi.mock('../catalog/productApi', () => ({ productApi: { get: vi.fn() } }))
vi.mock('../catalog/sellingPriceApi', async importOriginal => {
  const original = await importOriginal<typeof import('../catalog/sellingPriceApi')>()
  return { ...original, sellingPriceApi: { get: vi.fn(), update: vi.fn() } }
})

const customer: Customer = { id: '11111111-1111-4111-8111-111111111111', name: 'Fictional Hardware', contactName: null, email: 'fictional@example.test', phone: null,
  address: null, active: true, version: 0, createdAt: '2026-11-06T12:00:00Z', updatedAt: '2026-11-06T12:00:00Z' }
const product = { id: '22222222-2222-4222-8222-222222222222', name: 'Steel bolt', sku: 'BOLT-1', description: null, category: 'Hardware', unitOfMeasure: 'Each', active: true, version: 0,
  createdAt: customer.createdAt, updatedAt: customer.updatedAt, sellingPrice: null }
const price = { productId: product.id, currency: 'LKR' as const, amount: null, version: 0 }
const auth: AuthState = { user: { username: 'sales-test', organizationName: 'Fictional Test Organization', canWriteCatalog: false, canWriteInventory: false, canWriteCustomers: true, canWritePrices: true, canWriteOrders: false },
  loading: false, error: null, refresh: vi.fn(), signIn: vi.fn(), signOut: vi.fn() }
const page = { items: [customer], totalElements: 1, page: 0, size: 20, totalPages: 1 }
function open(path: string, state = auth) { render(<AuthContext.Provider value={state}><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthContext.Provider>) }

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(customerApi.list).mockResolvedValue(page)
  vi.mocked(customerApi.get).mockResolvedValue(customer)
  vi.mocked(customerApi.create).mockResolvedValue(customer)
  vi.mocked(customerApi.update).mockResolvedValue({ ...customer, version: 1 })
  vi.mocked(productApi.get).mockResolvedValue(product)
  vi.mocked(sellingPriceApi.get).mockResolvedValue(price)
  vi.mocked(sellingPriceApi.update).mockResolvedValue({ ...price, amount: '12.50', version: 1 })
})

describe('customer and selling price interactions', () => {
  it('creates a customer with normalized optional fields', async () => {
    const user = userEvent.setup()
    open('/customers/new')
    await user.type(screen.getByLabelText(/Customer name/), ' Fictional Hardware ')
    await user.type(screen.getByLabelText('Email'), 'fictional@example.test')
    await user.click(screen.getByRole('button', { name: 'Save customer' }))
    expect(await screen.findByText('Customer created successfully.')).toBeInTheDocument()
    expect(customerApi.create).toHaveBeenCalledWith({ name: 'Fictional Hardware', contactName: null, email: 'fictional@example.test', phone: null, address: null, active: true })
  })

  it('retains customer edits and links server validation to the field', async () => {
    vi.mocked(customerApi.create).mockRejectedValue(new ApiError({ status: 400, title: 'Validation failed', detail: 'Check fields.', errors: { name: 'Name is too long.' } }))
    const user = userEvent.setup()
    open('/customers/new')
    await user.type(screen.getByLabelText(/Customer name/), 'Fictional Hardware')
    await user.click(screen.getByRole('button', { name: 'Save customer' }))
    expect(await screen.findByText('Name is too long.')).toBeInTheDocument()
    expect(screen.getByLabelText(/Customer name/)).toHaveValue('Fictional Hardware')
    expect(screen.getByLabelText(/Customer name/)).toHaveAttribute('aria-describedby', 'name-error')
  })

  it('recovers from a stale customer edit by explicitly reloading', async () => {
    vi.mocked(customerApi.update).mockRejectedValue(new ApiError({ status: 409, title: 'Conflict', detail: 'This customer changed.' }))
    const user = userEvent.setup()
    open(`/customers/${customer.id}/edit`)
    await screen.findByDisplayValue(customer.name)
    await user.click(screen.getByRole('button', { name: 'Save customer' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('This customer changed.')
    vi.mocked(customerApi.get).mockResolvedValue({ ...customer, name: 'Latest customer', version: 1 })
    await user.click(screen.getByRole('button', { name: 'Reload latest customer (discards edits)' }))
    expect(await screen.findByDisplayValue('Latest customer')).toBeInTheDocument()
  })

  it('searches and filters customers, resetting pagination', async () => {
    const user = userEvent.setup()
    open('/customers')
    await screen.findByRole('link', { name: customer.name })
    await user.type(screen.getByLabelText('Search customers'), 'Hardware')
    await user.selectOptions(screen.getByLabelText('Status'), 'false')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() => expect(customerApi.list).toHaveBeenLastCalledWith({ q: 'Hardware', active: 'false', page: 0, size: 20 }, expect.any(AbortSignal)))
  })

  it('offers retry after customer loading fails and then shows the empty state', async () => {
    vi.mocked(customerApi.list).mockRejectedValueOnce(new Error('Network')).mockResolvedValue({ ...page, items: [], totalElements: 0, totalPages: 0 })
    open('/customers')
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach TradeSphere')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText('No customers found')).toBeInTheDocument()
  })

  it('catalog permission does not permit customer management', () => {
    open('/customers/new', { ...auth, user: { ...auth.user!, canWriteCatalog: true, canWriteCustomers: false } })
    expect(screen.getByRole('alert')).toHaveTextContent('Customer write permission is required')
    expect(customerApi.create).not.toHaveBeenCalled()
  })

  it('sends decimal strings and expected price version without number conversion', async () => {
    const user = userEvent.setup()
    open(`/products/${product.id}/selling-price`)
    expect(await screen.findByText('Not set')).toBeInTheDocument()
    await user.type(screen.getByLabelText(/New selling price/), '12.50')
    await user.click(screen.getByRole('button', { name: 'Save selling price' }))
    expect(await screen.findByText('Selling price updated successfully.')).toBeInTheDocument()
    expect(sellingPriceApi.update).toHaveBeenCalledWith(product.id, '12.50', 0)
  })

  it('rejects extra decimal places without sending a request', async () => {
    const user = userEvent.setup()
    open(`/products/${product.id}/selling-price`)
    await screen.findByLabelText(/New selling price/)
    await user.type(screen.getByLabelText(/New selling price/), '0.001')
    await user.click(screen.getByRole('button', { name: 'Save selling price' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Check the highlighted selling price.')
    expect(sellingPriceApi.update).not.toHaveBeenCalled()
  })

  it('retains a stale price entry until explicit reload and then uses the new version', async () => {
    vi.mocked(sellingPriceApi.update).mockRejectedValueOnce(new ApiError({ status: 409, title: 'Conflict', detail: 'This selling price changed.' }))
    const user = userEvent.setup()
    open(`/products/${product.id}/selling-price`)
    await screen.findByLabelText(/New selling price/)
    await user.type(screen.getByLabelText(/New selling price/), '20.00')
    await user.click(screen.getByRole('button', { name: 'Save selling price' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('This selling price changed.')
    expect(screen.getByLabelText(/New selling price/)).toHaveValue('20.00')
    vi.mocked(sellingPriceApi.get).mockResolvedValue({ ...price, amount: '15.00', version: 1 })
    await user.click(screen.getByRole('button', { name: 'Reload latest price (discards entry)' }))
    expect(await screen.findByText('LKR 15.00 per Each')).toBeInTheDocument()
    await user.type(screen.getByLabelText(/New selling price/), '0')
    await user.click(screen.getByRole('button', { name: 'Save selling price' }))
    await waitFor(() => expect(sellingPriceApi.update).toHaveBeenLastCalledWith(product.id, '0', 1))
  })

  it('catalog-only users can read price but cannot edit it', async () => {
    open(`/products/${product.id}/selling-price`, { ...auth, user: { ...auth.user!, canWriteCatalog: true, canWritePrices: false } })
    expect(await screen.findByText('Selling-price write permission is required to change this price.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Save selling price' })).not.toBeInTheDocument()
  })

  it('formats the maximum amount and zero without rounding', () => {
    expect(formatLkr('999999999999.99')).toBe('LKR 999,999,999,999.99')
    expect(formatLkr('0.00')).toBe('LKR 0.00')
    expect(formatLkr(null)).toBe('Not set')
  })
})
