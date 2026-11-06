import { defineConfig, devices } from '@playwright/test'
import { loadEnv } from 'vite'

const local = loadEnv('', '..', '')
const password = process.env.E2E_PASSWORD
if (!password) throw new Error('Set E2E_PASSWORD to a disposable test password before running browser tests.')

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: 'list',
  use: { baseURL: 'http://127.0.0.1:5174', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], channel: process.env.E2E_BROWSER } }],
  webServer: [
    {
      command: 'java -jar ../backend/target/tradesphere-0.0.1-SNAPSHOT.jar',
      url: 'http://127.0.0.1:8081/actuator/health/readiness',
      timeout: 60_000,
      reuseExistingServer: false,
      env: {
        SERVER_PORT: '8081',
        POSTGRES_DB: process.env.E2E_POSTGRES_DB ?? 'tradesphere_e2e',
        POSTGRES_USER: process.env.POSTGRES_USER ?? local.POSTGRES_USER ?? 'tradesphere',
        POSTGRES_PASSWORD: process.env.POSTGRES_PASSWORD ?? local.POSTGRES_PASSWORD ?? '',
        POSTGRES_PORT: process.env.POSTGRES_PORT ?? local.POSTGRES_PORT ?? '5432',
        BOOTSTRAP_ENABLED: 'true',
        BOOTSTRAP_USERNAME: 'e2e-catalog-editor',
        BOOTSTRAP_PASSWORD: password,
        BOOTSTRAP_ORGANIZATION_NAME: 'TradeSphere Fictional Browser Test Organization',
      },
    },
    { command: 'npm run dev -- --port 5174', url: 'http://127.0.0.1:5174',
      reuseExistingServer: false, env: { API_PROXY_TARGET: 'http://127.0.0.1:8081' } },
  ],
})
