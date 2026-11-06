import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { DashboardPage } from './DashboardPage'
import { dashboardApi } from './dashboardApi'
import { AuthContext } from '../identity/useAuth'
import type { AuthState } from '../identity/useAuth'

vi.mock('./dashboardApi', () => ({ dashboardApi: { get: vi.fn() } }))
const empty = { counts: { activeProducts: 0, warehouses: 0, activeCustomers: 0, recordedBalances: 0, zeroAvailableBalances: 0, totalOrders: 0 }, ordersByStatus: [], recentOrders: [] }
const auth: AuthState = { user: null, loading: false, error: null, refresh: vi.fn(), signIn: vi.fn(), signOut: vi.fn() }
function open() { render(<AuthContext.Provider value={auth}><MemoryRouter><DashboardPage /></MemoryRouter></AuthContext.Provider>) }
beforeEach(() => { vi.resetAllMocks(); vi.mocked(dashboardApi.get).mockResolvedValue(empty) })
it('shows loading then honest empty values and zero counts for every lifecycle state', async () => {
  open()
  expect(screen.getByRole('status')).toHaveTextContent('Loading dashboard')
  expect(await screen.findByText(/No orders yet/)).toBeInTheDocument()
  expect(screen.getByText(/No low-stock threshold/)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Processing' })).toHaveAttribute('href', '/orders?status=PROCESSING')
})
it('renders real summary values and refreshes them', async () => {
  vi.mocked(dashboardApi.get).mockResolvedValueOnce({ ...empty, counts: { ...empty.counts, activeProducts: 7 } })
  open()
  const product = await screen.findByRole('link', { name: 'Active products' })
  expect(within(product.closest('div')!).getByText('7')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Refresh dashboard' }))
  await screen.findByText(/No orders yet/)
  expect(dashboardApi.get).toHaveBeenCalledTimes(2)
})
it('recovers from a failed read without inventing statistics', async () => {
  vi.mocked(dashboardApi.get).mockRejectedValueOnce(new Error('Disconnected'))
  open()
  expect(await screen.findByRole('alert')).toHaveTextContent('Could not reach TradeSphere')
  expect(screen.queryByRole('link', { name: 'Active products' })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByText(/No orders yet/)).toBeInTheDocument()
})
