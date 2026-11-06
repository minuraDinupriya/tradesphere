import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { productApi } from './features/catalog/productApi'
import type { Product, ProductPage } from './features/catalog/productApi'
import { AuthContext } from './features/identity/useAuth'
import type { AuthState } from './features/identity/useAuth'
import { ApiError } from './shared/api/http'

vi.mock('./features/catalog/productApi', () => ({ productApi: {
  list: vi.fn(), categories: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn(),
} }))

const product: Product = { id: '0c995ca5-f5e2-4e4b-9655-b24b05919b41', sku: 'BOLT-01', name: 'Steel bolt', description: 'Fictional test product',
  category: 'Hardware', unitOfMeasure: 'Each', active: true, version: 0, createdAt: '2026-11-06T09:00:00Z', updatedAt: '2026-11-06T09:00:00Z', sellingPrice: null }
const page: ProductPage = { items: [product], totalElements: 1, page: 0, size: 20, totalPages: 1 }
const editor: AuthState = { user: { username: 'test-editor', organizationName: 'Fictional Test Distributor', canWriteCatalog: true, canWriteInventory: false, canWriteCustomers: false, canWritePrices: false, canWriteOrders: false },
  loading: false, error: null, refresh: vi.fn(), signIn: vi.fn(), signOut: vi.fn() }

function renderApp(path = '/products', auth = editor) {
  return render(<AuthContext.Provider value={auth}><MemoryRouter initialEntries={[path]}><App /></MemoryRouter></AuthContext.Provider>)
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(productApi.list).mockResolvedValue(page)
  vi.mocked(productApi.categories).mockResolvedValue(['Hardware'])
  vi.mocked(productApi.get).mockResolvedValue(product)
  vi.mocked(productApi.create).mockResolvedValue(product)
  vi.mocked(productApi.update).mockResolvedValue({ ...product, version: 1 })
})

describe('catalog interactions', () => {
  it('shows loading then the product table and a working skip link', async () => {
    renderApp()
    expect(screen.getByRole('status')).toHaveTextContent('Loading products')
    expect(await screen.findByRole('link', { name: 'Steel bolt' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main-content')
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content')
  })

  it('submits search and category/status filters and clears them', async () => {
    const user = userEvent.setup()
    renderApp()
    await screen.findByRole('link', { name: 'Steel bolt' })
    await user.type(screen.getByLabelText('Search products'), 'bolt')
    await user.selectOptions(screen.getByLabelText('Category'), 'Hardware')
    await user.selectOptions(screen.getByLabelText('Status'), 'false')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() => expect(productApi.list).toHaveBeenLastCalledWith(
      { q: 'bolt', category: 'Hardware', active: 'false', page: 0, size: 20 }, expect.any(AbortSignal)))
    await user.click(screen.getByRole('button', { name: 'Clear' }))
    await waitFor(() => expect(productApi.list).toHaveBeenLastCalledWith(
      { q: '', category: '', active: '', page: 0, size: 20 }, expect.any(AbortSignal)))
  })

  it('moves between server pages and prevents navigation before the first page', async () => {
    vi.mocked(productApi.list).mockResolvedValue({ ...page, totalElements: 21, totalPages: 2 })
    renderApp()
    await screen.findByRole('link', { name: 'Steel bolt' })
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await waitFor(() => expect(productApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }), expect.any(AbortSignal)))
  })

  it('shows an honest empty state', async () => {
    vi.mocked(productApi.list).mockResolvedValue({ ...page, items: [], totalElements: 0, totalPages: 0 })
    renderApp()
    expect(await screen.findByText('Your catalog is ready for its first product')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('offers recovery after a request failure', async () => {
    vi.mocked(productApi.list).mockRejectedValueOnce(new Error('Network unavailable'))
    renderApp()
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach TradeSphere')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('link', { name: 'Steel bolt' })).toBeInTheDocument()
  })

  it('creates a product from user input and shows success', async () => {
    const user = userEvent.setup()
    renderApp('/products/new')
    await user.type(screen.getByLabelText(/Product name/), 'Steel bolt')
    await user.type(screen.getByLabelText(/SKU/), 'bolt-01')
    await user.type(screen.getByLabelText(/Category/), 'Hardware')
    await user.click(screen.getByRole('button', { name: 'Save product' }))
    expect(await screen.findByText('Product created successfully.')).toBeInTheDocument()
    expect(productApi.create).toHaveBeenCalledWith({ sku: 'bolt-01', name: 'Steel bolt', description: null, category: 'Hardware', unitOfMeasure: 'Each', active: true })
  })

  it('links server validation errors to fields and keeps entered values', async () => {
    vi.mocked(productApi.create).mockRejectedValue(new ApiError({ status: 400, title: 'Validation failed', detail: 'Check the highlighted fields.', errors: { sku: 'SKU is invalid.' } }))
    renderApp('/products/new')
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/Product name/), 'Bolt')
    await user.type(screen.getByLabelText(/SKU/), 'B-1')
    await user.type(screen.getByLabelText(/Category/), 'Hardware')
    await user.click(screen.getByRole('button', { name: 'Save product' }))
    expect(await screen.findByText('SKU is invalid.')).toBeInTheDocument()
    expect(screen.getByLabelText(/SKU/)).toHaveAttribute('aria-describedby', 'sku-error')
    expect(screen.getByLabelText(/Product name/)).toHaveValue('Bolt')
    expect(screen.getByRole('alert')).toHaveFocus()
  })

  it('does not silently overwrite a stale edit and allows explicit reload', async () => {
    vi.mocked(productApi.update).mockRejectedValue(new ApiError({ status: 409, title: 'Product conflict', detail: 'This product changed. Reload the latest product.' }))
    vi.mocked(productApi.get).mockResolvedValueOnce(product).mockResolvedValue({ ...product, name: 'Other edit', version: 1 })
    const user = userEvent.setup()
    renderApp(`/products/${product.id}/edit`)
    await screen.findByDisplayValue('Steel bolt')
    await user.clear(screen.getByLabelText(/Product name/))
    await user.type(screen.getByLabelText(/Product name/), 'My edit')
    await user.click(screen.getByRole('button', { name: 'Save product' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('This product changed')
    expect(screen.getByLabelText(/Product name/)).toHaveValue('My edit')
    expect(productApi.update).toHaveBeenCalledWith(product.id, expect.objectContaining({ name: 'My edit' }), 0)
    await user.click(screen.getByRole('button', { name: 'Reload latest product (discards edits)' }))
    expect(await screen.findByDisplayValue('Other edit')).toBeInTheDocument()
  })

  it('provides read-only detail access without create or edit controls', async () => {
    renderApp('/products', { ...editor, user: { ...editor.user!, canWriteCatalog: false } })
    await screen.findByRole('link', { name: 'Steel bolt' })
    expect(screen.queryByRole('link', { name: /Add product/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Edit Steel bolt' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'View Steel bolt' }))
    expect(await screen.findByRole('heading', { name: 'Steel bolt' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Edit product' })).not.toBeInTheDocument()
  })

  it('rejects direct edit navigation for a read-only user', () => {
    renderApp(`/products/${product.id}/edit`, { ...editor, user: { ...editor.user!, canWriteCatalog: false } })
    expect(screen.getByRole('alert')).toHaveTextContent('read-only')
    expect(productApi.get).not.toHaveBeenCalled()
  })

  it('reports a failed sign-in and clears the password', async () => {
    const signIn = vi.fn().mockRejectedValue(new ApiError({ status: 401, title: 'Sign-in failed', detail: 'The username or password is incorrect.' }))
    renderApp('/products', { ...editor, user: null, signIn })
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Username'), 'test-viewer')
    await user.type(screen.getByLabelText('Password'), 'incorrect-test-input')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('username or password is incorrect')
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })
})
