import { expect, test } from '@playwright/test'

test('cancels eligible orders and fulfills processing orders once, with keyboard dialogs and auditable stock changes', async ({ page }, testInfo) => {
  const suffix = Date.now().toString()
  await page.goto('/orders')
  await page.getByLabel('Username').fill('e2e-catalog-editor')
  await page.getByLabel('Password', { exact: true }).fill(process.env.E2E_PASSWORD!)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Orders', exact: true })).toBeVisible()
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json() as { headerName: string; token: string }
  const headers = { [csrf.headerName]: csrf.token }
  const customerResponse = await page.request.post('/api/v1/customers', { headers, data: { name: `Fictional lifecycle customer ${suffix}`, active: true } })
  expect(customerResponse.status()).toBe(201)
  const customer = await customerResponse.json() as { id: string }
  const warehouseResponse = await page.request.post('/api/v1/warehouses', { headers, data: { code: `LIFE-${suffix}`, name: `Lifecycle warehouse ${suffix}`, active: true } })
  expect(warehouseResponse.status()).toBe(201)
  const warehouse = await warehouseResponse.json() as { id: string }
  const products: { id: string; sku: string; quantity: number; onHand: number }[] = []
  for (const [label, quantity, onHand] of [['Bolt', 2, 10], ['Washer', 3, 20]] as const) {
    const sku = `LIFE-${label.toUpperCase()}-${suffix}`
    const productResponse = await page.request.post('/api/v1/products', { headers, data: { sku, name: `Fictional lifecycle ${label}`, category: 'Hardware', unitOfMeasure: 'Each', active: true } })
    expect(productResponse.status()).toBe(201)
    const product = await productResponse.json() as { id: string }
    products.push({ ...product, sku, quantity, onHand })
    expect((await page.request.put(`/api/v1/products/${product.id}/selling-price`, { headers, data: { amount: '12.50', version: 0 } })).status()).toBe(200)
    expect((await page.request.post('/api/v1/inventory/receipts', { headers, data: { requestId: crypto.randomUUID(), warehouseId: warehouse.id, productId: product.id, quantity: onHand } })).status()).toBe(201)
  }
  async function pending() {
    const response = await page.request.post('/api/v1/orders', { headers, data: { requestId: crypto.randomUUID(), customerId: customer.id, warehouseId: warehouse.id,
      items: products.map(({ id, quantity }) => ({ productId: id, quantity })) } })
    expect(response.status()).toBe(201)
    return (await response.json() as { order: { id: string } }).order.id
  }
  async function act(label: string) {
    await page.getByRole('button', { name: label, exact: true }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByRole('dialog').getByRole('button', { name: 'Go back' })).toBeFocused()
    await page.getByRole('dialog').getByRole('button', { name: label, exact: true }).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()
  }
  async function stock(fulfilled: boolean) {
    const response = await page.request.get(`/api/v1/inventory?warehouseId=${warehouse.id}&size=100`)
    expect(response.status()).toBe(200)
    const rows = (await response.json() as { items: { productId: string; onHand: number; reserved: number; available: number }[] }).items
    for (const product of products) expect(rows.find(row => row.productId === product.id)).toMatchObject({ onHand: product.onHand - (fulfilled ? product.quantity : 0), reserved: 0, available: product.onHand - (fulfilled ? product.quantity : 0) })
  }

  const pendingId = await pending()
  await page.goto(`/orders/${pendingId}`)
  const cancel = page.getByRole('button', { name: 'Cancel order', exact: true })
  await cancel.click()
  await expect(page.getByRole('dialog')).toContainText('No stock is reserved')
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).not.toBeVisible()
  await expect(cancel).toBeFocused()
  expect((await (await page.request.get(`/api/v1/orders/${pendingId}`)).json() as { status: string }).status).toBe('PENDING')
  await act('Cancel order')
  await expect(page.getByText('Cancelled successfully.', { exact: false })).toBeVisible()
  await expect(page.locator('.order-history li')).toHaveCount(2)
  await expect(page.getByText('No release or fulfillment movements recorded.')).toBeVisible()
  await stock(false)

  const cancelledId = await pending()
  await page.goto(`/orders/${cancelledId}`)
  await act('Confirm order')
  await expect(page.getByText('Order confirmed. All items were reserved successfully.')).toBeVisible()
  await act('Cancel order')
  await expect(page.getByText('Cancelled successfully.', { exact: false })).toBeVisible()
  const released = page.getByRole('table', { name: 'Order inventory movements' })
  await expect(released.getByRole('row')).toHaveCount(3)
  await expect(released.getByText('Reservation release', { exact: true })).toHaveCount(2)
  await page.screenshot({ path: testInfo.outputPath('cancelled-order.png'), fullPage: true })
  await stock(false)

  const fulfilledId = await pending()
  await page.goto(`/orders/${fulfilledId}`)
  await act('Confirm order')
  await expect(page.getByText('Order confirmed. All items were reserved successfully.')).toBeVisible()
  await act('Start processing')
  await expect(page.getByText('Processing started. Stock remains reserved; cancellation is now unavailable.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Cancel order', exact: true })).not.toBeVisible()
  await page.getByRole('button', { name: 'Fulfill order', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('physical on-hand stock will decrease')
  await page.screenshot({ path: testInfo.outputPath('fulfillment-dialog.png'), fullPage: true })
  await page.getByRole('dialog').getByRole('button', { name: 'Go back' }).click()
  let drop = true
  await page.route(`**/api/v1/orders/${fulfilledId}/fulfill`, async route => {
    if (drop) { drop = false; expect((await route.fetch()).status()).toBe(200); await route.abort('failed') }
    else await route.continue()
  })
  await act('Fulfill order')
  await expect(page.getByRole('alert')).toContainText('may already have completed')
  await page.getByRole('button', { name: 'Retry same action', exact: true }).click()
  await expect(page.getByText('This action was already recorded. Stock was not changed again. Current status: Fulfilled.', { exact: true })).toBeVisible()
  await expect(page.locator('.order-history li')).toHaveCount(4)
  await expect(page.getByRole('table', { name: 'Order inventory movements' }).getByRole('row')).toHaveCount(3)
  await expect(page.getByRole('button', { name: 'Fulfill order', exact: true })).not.toBeVisible()
  await stock(true)
  await page.screenshot({ path: testInfo.outputPath('fulfilled-order.png'), fullPage: true })
  await page.reload()
  await expect(page.locator('.status-badge').filter({ hasText: /^Fulfilled$/ })).toBeVisible()
  await expect(page.locator('.order-history li')).toHaveCount(4)
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('lifecycle-mobile.png'), fullPage: true })
})
