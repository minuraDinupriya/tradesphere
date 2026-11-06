import { expect, test } from '@playwright/test'

test('multi-item order keeps price snapshots and safely confirms all reservations after a lost response', async ({ page }, testInfo) => {
  const suffix = Date.now().toString()
  const customerName = `Fictional order customer ${suffix}`
  const warehouseName = `Order test warehouse ${suffix}`
  const firstName = `Order bolt ${suffix}`
  const secondName = `Order washer ${suffix}`
  const firstSku = `OB-${suffix}`
  const secondSku = `OW-${suffix}`
  await page.goto('/orders')
  await page.getByLabel('Username').fill('e2e-catalog-editor')
  await page.getByLabel('Password', { exact: true }).fill(process.env.E2E_PASSWORD!)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Orders', exact: true })).toBeVisible()

  // Isolated setup uses the authenticated API; the complete order journey below uses the UI.
  const csrfResponse = await page.request.get('/api/v1/auth/csrf')
  const csrf = await csrfResponse.json() as { headerName: string; token: string }
  const headers = { [csrf.headerName]: csrf.token }
  const customerResponse = await page.request.post('/api/v1/customers', { headers, data: { name: customerName, active: true } })
  expect(customerResponse.status()).toBe(201)
  const customer = await customerResponse.json() as { id: string }
  const warehouseResponse = await page.request.post('/api/v1/warehouses', { headers, data: { code: `ORDER-${suffix}`, name: warehouseName, active: true } })
  expect(warehouseResponse.status()).toBe(201)
  const warehouse = await warehouseResponse.json() as { id: string }
  const productIds: string[] = []
  for (const [sku, name, amount, quantity] of [[firstSku, firstName, '12.50', 5], [secondSku, secondName, '0.10', 8]] as const) {
    const productResponse = await page.request.post('/api/v1/products', { headers, data: { sku, name, category: 'Hardware', unitOfMeasure: 'Each', active: true } })
    expect(productResponse.status()).toBe(201)
    const product = await productResponse.json() as { id: string }
    productIds.push(product.id)
    expect((await page.request.put(`/api/v1/products/${product.id}/selling-price`, { headers, data: { amount, version: 0 } })).status()).toBe(200)
    expect((await page.request.post('/api/v1/inventory/receipts', { headers, data: { requestId: crypto.randomUUID(), warehouseId: warehouse.id, productId: product.id, quantity } })).status()).toBe(201)
  }

  await page.getByRole('link', { name: 'Create order', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Create order', exact: true })).toBeVisible()
  const choices = page.getByRole('group', { name: 'Order choices', exact: true })
  await choices.getByRole('combobox', { name: /^Customer/ }).selectOption(customer.id)
  await choices.getByRole('combobox', { name: /^Warehouse/ }).selectOption(warehouse.id)
  const products = page.getByRole('group', { name: 'Add order products', exact: true })
  for (const [sku, name, id, quantity] of [[firstSku, firstName, productIds[0], '2'], [secondSku, secondName, productIds[1], '3']] as const) {
    await products.getByLabel('Find an active product').fill(sku)
    await products.getByRole('button', { name: 'Find products', exact: true }).click()
    await products.getByLabel('Product to add').selectOption(id)
    await products.getByRole('button', { name: 'Add product to order' }).click()
    await page.getByRole('spinbutton', { name: `Quantity for ${name}` }).fill(quantity)
  }
  await expect(choices.getByRole('combobox', { name: /^Warehouse/ })).toHaveValue(warehouse.id)
  await page.screenshot({ path: testInfo.outputPath('create-order.png'), fullPage: true })
  await page.getByRole('button', { name: 'Create pending order', exact: true }).click()
  await expect(page.getByText('Pending order created. No stock is reserved yet.', { exact: true })).toBeVisible()
  await expect(page.locator('.status-badge').filter({ hasText: /^Pending$/ })).toBeVisible()
  const orderId = new URL(page.url()).pathname.split('/').pop()!
  const lines = page.getByRole('table', { name: 'Order items and snapshotted prices', exact: true })
  await expect(lines.getByText('LKR 12.50', { exact: true })).toBeVisible()
  await expect(page.getByText('LKR 25.30', { exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('pending-order.png'), fullPage: true })

  // A later master-price edit must not reprice an already-created pending order.
  expect((await page.request.put(`/api/v1/products/${productIds[0]}/selling-price`, { headers, data: { amount: '99.99', version: 1 } })).status()).toBe(200)
  await page.reload()
  await expect(lines.getByText('LKR 12.50', { exact: true })).toBeVisible()
  await expect(page.getByText('LKR 25.30', { exact: true })).toBeVisible()
  let dropNextResponse = true
  await page.route(`**/api/v1/orders/${orderId}/confirm`, async route => {
    if (dropNextResponse) {
      dropNextResponse = false
      const response = await route.fetch()
      expect(response.status()).toBe(200)
      await route.abort('failed')
    } else await route.continue()
  })
  await page.getByRole('button', { name: 'Confirm order', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Confirm order', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Confirmation may already have completed')
  await page.getByRole('button', { name: 'Retry confirmation', exact: true }).click()
  await expect(page.getByText('This order was already confirmed. Stock was not reserved again.', { exact: true })).toBeVisible()
  await expect(page.locator('.status-badge').filter({ hasText: /^Confirmed$/ })).toBeVisible()
  await expect(page.getByText('LKR 25.30', { exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('confirmed-order.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('order-mobile.png'), fullPage: true })
  await page.setViewportSize({ width: 1280, height: 720 })

  await page.getByRole('link', { name: 'View reserved inventory', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Inventory', exact: true })).toBeVisible()
  const boltBalance = page.getByRole('row').filter({ hasText: firstSku })
  await expect(boltBalance.getByRole('cell').nth(3)).toHaveText('5')
  await expect(boltBalance.getByRole('cell').nth(4)).toHaveText('2')
  await expect(boltBalance.getByRole('cell').nth(5)).toHaveText('3')
  const washerBalance = page.getByRole('row').filter({ hasText: secondSku })
  await expect(washerBalance.getByRole('cell').nth(3)).toHaveText('8')
  await expect(washerBalance.getByRole('cell').nth(4)).toHaveText('3')
  await expect(washerBalance.getByRole('cell').nth(5)).toHaveText('5')
  await page.screenshot({ path: testInfo.outputPath('reserved-inventory.png'), fullPage: true })
  await page.getByRole('link', { name: 'Orders', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Orders', exact: true })).toBeVisible()
  await page.getByLabel('Search orders').fill(customerName)
  await page.getByRole('button', { name: 'Search', exact: true }).click()
  await expect(page.getByRole('link', { name: `View order ${orderId}`, exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('orders.png'), fullPage: true })
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible()
})
