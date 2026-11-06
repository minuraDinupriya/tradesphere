import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import App from './App'

function renderApp(path = '/') {
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
}

describe('TradeSphere foundation navigation', () => {
  it('shows an honest foundation preview within the shared layout', () => {
    renderApp()

    expect(screen.getByRole('heading', { name: 'Welcome to TradeSphere.' })).toBeInTheDocument()
    expect(screen.getByText(/no live products, inventory, or orders/i)).toBeInTheDocument()
    expect(screen.getAllByText('Planned', { exact: true })).toHaveLength(3)
    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page')
  })

  it('lets a user recover from an unknown route', async () => {
    const user = userEvent.setup()
    renderApp('/does-not-exist')

    expect(screen.getByRole('heading', { name: /get you back on track/i })).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: 'Return to overview' }))

    expect(screen.getByRole('heading', { name: 'Welcome to TradeSphere.' })).toBeInTheDocument()
    expect(screen.queryByText('The page you requested does not exist.')).not.toBeInTheDocument()
  })

  it('provides a keyboard skip link to the main content', () => {
    renderApp()

    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#main-content')
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main-content')
  })
})
