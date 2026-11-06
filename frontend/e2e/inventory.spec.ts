import { expect, test } from '@playwright/test'

test('warehouse operations and retry-safe stock receipt update inventory and movement history', async ({ page }, testInfo) => {
  test.setTimeout(60_000)
  const suffix = Date.now().toString()
  const productName = `Packing tape ${suffix}`
  const sku = `INV-${suffix}`
  const warehouseName = `Colombo QA ${suffix}`
  const warehouseCode = `WH-${suffix}`
  const reference = `DEL-${suffix}`
  const retryReference = `RETRY-${suffix}`
  await page.goto('/products')
  await page.getByLabel('Username').fill('e2e-catalog-editor')
  await page.getByLabel('Password', { exact: true }).fill(process.env.E2E_PASSWORD!)
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page.getByRole('link', { name: 'Add product' }).click()
  await page.getByLabel('Product name').fill(productName)
  await page.getByLabel('SKU', { exact: false }).fill(sku)
  await page.getByLabel('Category', { exact: false }).fill('Packaging')
  await page.getByRole('button', { name: 'Save product' }).click()
  await expect(page.getByText('Product created successfully.', { exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'Warehouses', exact: true }).click()
  await page.getByRole('link', { name: 'Add warehouse' }).click()
  await page.getByLabel('Warehouse name').fill(warehouseName)
  await page.getByLabel('Warehouse code').fill(warehouseCode)
  await page.getByLabel('Location', { exact: true }).fill('Fictional browser-test location')
  await page.getByRole('button', { name: 'Save warehouse' }).click()
  await expect(page.getByText('Warehouse created successfully.', { exact: true })).toBeVisible()
  await page.getByLabel('Search warehouses').fill(warehouseCode)
  await page.getByRole('button', { name: 'Search', exact: true }).click()
  await page.getByRole('link', { name: warehouseName, exact: true }).click()
  const warehouseId = new URL(page.url()).pathname.split('/').pop()!
  await page.getByRole('link', { name: 'Edit warehouse', exact: true }).click()
  await page.getByLabel('Location', { exact: true }).fill('Updated fictional browser-test location')
  await page.getByRole('button', { name: 'Save warehouse' }).click()
  await expect(page.getByText('Warehouse updated successfully.', { exact: true })).toBeVisible()
  await page.getByLabel('Search warehouses').fill(warehouseCode)
  await page.getByRole('button', { name: 'Search', exact: true }).click()
  await expect(page.getByRole('cell', { name: 'Updated fictional browser-test location' })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('warehouses.png'), fullPage: true })

  await page.getByRole('link', { name: warehouseName, exact: true }).click()
  await page.getByRole('link', { name: 'Receive stock', exact: true }).click()
  await page.getByLabel('Find an active product').fill(sku)
  await page.getByRole('button', { name: 'Find products', exact: true }).click()
  await page.getByLabel(/^Product/).selectOption({ label: `${productName} (${sku})` })
  await page.getByLabel('Quantity received', { exact: false }).fill('20')
  await page.getByLabel('Reference', { exact: true }).fill(reference)
  await page.screenshot({ path: testInfo.outputPath('stock-receiving.png'), fullPage: true })
  await page.getByRole('button', { name: 'Receive stock', exact: true }).click()
  await expect(page.getByText('Stock received successfully.', { exact: true })).toBeVisible()
  const balanceRow = page.getByRole('row').filter({ hasText: sku })
  await expect(balanceRow.getByRole('cell').nth(3)).toHaveText('20')
  await expect(balanceRow.getByRole('cell').nth(4)).toHaveText('0')
  await expect(balanceRow.getByRole('cell').nth(5)).toHaveText('20')

  await page.getByRole('link', { name: 'Receive stock' }).click()
  await page.getByLabel(/^Warehouse/).selectOption(warehouseId)
  await page.getByLabel('Find an active product').fill(sku)
  await page.getByRole('button', { name: 'Find products', exact: true }).click()
  await page.getByLabel(/^Product/).selectOption({ label: `${productName} (${sku})` })
  await page.getByLabel('Quantity received', { exact: false }).fill('5')
  await page.getByLabel('Reference', { exact: true }).fill(retryReference)
  let dropNextResponse = true
  await page.route('**/api/v1/inventory/receipts', async route => {
    if (dropNextResponse) {
      dropNextResponse = false
      const response = await route.fetch() // The real server commits; only its browser response is lost.
      expect(response.status()).toBe(201)
      await route.abort('failed')
    } else await route.continue()
  })
  await page.getByRole('button', { name: 'Receive stock', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('could not confirm')
  await expect(page.getByLabel('Quantity received', { exact: false })).toBeDisabled()
  await page.getByRole('button', { name: 'Retry same receipt', exact: true }).click()
  await expect(page.getByText('This receipt was already recorded. Stock was not increased again.', { exact: true })).toBeVisible()
  await expect(balanceRow.getByRole('cell').nth(3)).toHaveText('25')
  await expect(balanceRow.getByRole('cell').nth(5)).toHaveText('25')
  await page.screenshot({ path: testInfo.outputPath('inventory.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('inventory-mobile.png'), fullPage: true })
  await page.setViewportSize({ width: 1280, height: 720 })

  await page.getByRole('link', { name: 'Stock movements', exact: true }).click()
  await page.getByLabel('Search movements').fill(retryReference)
  await page.getByRole('button', { name: 'Search', exact: true }).click()
  await expect(page.getByRole('row').filter({ hasText: retryReference })).toHaveCount(1)
  await expect(page.getByRole('cell', { name: '+5 Each', exact: true })).toBeVisible()
  await page.screenshot({ path: testInfo.outputPath('stock-movements.png'), fullPage: true })
  await page.getByRole('link', { name: `View receipt ${retryReference}`, exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Stock receipt', exact: true })).toBeVisible()
  await expect(page.locator('dl')).toContainText('e2e-catalog-editor')
  await expect(page.getByText(retryReference, { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Sign out', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeVisible()
})
