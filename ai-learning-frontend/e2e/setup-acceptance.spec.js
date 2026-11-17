import { test, expect } from '@playwright/test'

// Controlled API fixtures exercise the browser workflow; they do not validate deployment.
const CODE = 'mock-install-code'
const CSRF = 'mock-csrf-token'
const PASSWORD = 'mock-admin-password'
const ok = data => ({ code: '200', msg: 'ok', data })

async function mockSetup(page, { mismatch = false, classification = 'EMPTY', checkStatus = 200, completeStatus = 202 } = {}) {
  let session = false
  let polls = 0
  const requests = []
  await page.route('**/backend/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/backend', '')
    const method = request.method()
    requests.push({ path, method, headers: request.headers(), body: request.postData() ? request.postDataJSON() : null })
    let status = 200
    let body
    if (path === '/api/setup/status') body = ok({ state: 'UNINITIALIZED', setupAllowed: true })
    else if (path === '/api/setup/session' && method === 'POST') {
      session = true
      body = ok({ csrfToken: CSRF })
    } else if (path === '/api/setup/session' && method === 'GET') {
      status = session ? 200 : 401
      body = session ? ok({ csrfToken: CSRF }) : { code: '401', msg: 'unauthorized', data: {} }
    } else if (path === '/api/setup/database/check') {
      status = checkStatus
      body = status === 200
        ? ok({ checkId: 'mock-check', classification, compatible: true })
        : { code: String(status), msg: 'mock authorization error', data: {} }
    } else if (path === '/api/setup/complete') {
      status = completeStatus
      body = status === 202
        ? { code: '202', msg: 'accepted', data: { operationId: 'mock-operation' } }
        : { code: String(status), msg: 'mock revision conflict', data: {} }
    } else if (path === '/api/setup/operations/mock-operation') {
      polls++
      if (polls === 1 && !mismatch) {
        status = 503
        body = { code: '503', msg: 'restarting', data: {} }
      } else {
        body = ok({
          operationId: 'mock-operation',
          status: 'APPLIED',
          targetRevision: 'revision-2',
          activeRevision: mismatch ? 'revision-1' : 'revision-2',
          updatedAt: '2026-11-05T00:00:00Z',
        })
      }
    } else {
      status = 404
      body = { code: '404', msg: 'unexpected mock request', data: {} }
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  })
  return requests
}

async function completeWizard(page) {
  await page.goto('/setup')
  await page.getByLabel('安装码', { exact: true }).fill(CODE)
  await page.getByRole('button', { name: '继续', exact: true }).click()
  await expect(page.getByRole('heading', { name: '连接数据库' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '连接数据库' })).toBeVisible()
  await page.getByRole('button', { name: '检查数据库' }).click()
  await expect(page.getByText('空数据库，可以初始化')).toBeVisible()
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByLabel('用户名', { exact: true }).fill('mock-admin')
  await page.getByLabel('昵称', { exact: true }).fill('Mock Admin')
  await page.getByLabel('密码', { exact: true }).fill(PASSWORD)
  await page.getByLabel('确认密码', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '跳过 AI 配置' }).click()
  await expect(page.getByRole('heading', { name: '确认并完成安装' })).toBeVisible()
  await page.getByRole('button', { name: '完成安装' }).click()
}

for (const width of [375, 768, 1080, 1440]) {
  test(`mocked setup entry fits ${width}px viewport`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    await mockSetup(page)
    await page.goto('/setup')
    await expect(page.getByLabel('安装码', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '继续', exact: true })).toBeVisible()
    const dimensions = await page.evaluate(() => ({
      viewport: document.documentElement.clientWidth,
      content: document.documentElement.scrollWidth,
    }))
    expect(dimensions.content).toBeLessThanOrEqual(dimensions.viewport)
  })
}

test('mocked setup refreshes CSRF, skips AI, and waits through restart', async ({ page }) => {
  const requests = await mockSetup(page)
  await completeWizard(page)
  await expect(page.getByText('安装已由服务确认完成', { exact: false })).toBeVisible({ timeout: 15000 })

  const exchange = requests.find(item => item.path === '/api/setup/session' && item.method === 'POST')
  const check = requests.find(item => item.path === '/api/setup/database/check')
  const complete = requests.filter(item => item.path === '/api/setup/complete')
  expect(exchange.body).toEqual({ code: CODE })
  expect(requests.filter(item => item.path === '/api/setup/session' && item.method === 'GET').length).toBeGreaterThanOrEqual(2)
  expect(check.headers['x-csrf-token']).toBe(CSRF)
  expect(check.body.database).toEqual({ mode: 'MANAGED' })
  expect(complete).toHaveLength(1)
  expect(complete[0].headers['x-csrf-token']).toBe(CSRF)
  expect(complete[0].headers['idempotency-key']).toBeTruthy()
  expect(complete[0].body.checkId).toBe('mock-check')
  expect(complete[0].body.mode).toBe('NEW')
  expect(complete[0].body.admin).toEqual({ username: 'mock-admin', nickname: 'Mock Admin', password: PASSWORD })
  expect(Object.values(complete[0].body.roles).every(role => !role.enabled)).toBe(true)
  const storage = await page.evaluate(() => JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }))
  for (const secret of [CODE, CSRF, PASSWORD]) expect(storage).not.toContain(secret)
})

test('mocked existing database adopts an existing administrator', async ({ page }) => {
  const requests = await mockSetup(page, { classification: 'KNOWN_EXISTING' })
  await page.goto('/setup')
  await page.getByLabel('安装码', { exact: true }).fill(CODE)
  await page.getByRole('button', { name: '继续', exact: true }).click()
  await page.getByRole('button', { name: '检查数据库' }).click()
  await expect(page.getByText('检测到已有的 EduLoop 数据库')).toBeVisible()
  await page.getByRole('button', { name: '下一步' }).click()
  await expect(page.getByRole('heading', { name: '验证现有管理员' })).toBeVisible()
  await expect(page.getByLabel('昵称', { exact: true })).toHaveCount(0)
  await expect(page.getByLabel('确认密码', { exact: true })).toHaveCount(0)
  await page.getByLabel('用户名', { exact: true }).fill('existing-admin')
  await page.getByLabel('密码', { exact: true }).fill(PASSWORD)
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByRole('button', { name: '跳过 AI 配置' }).click()
  await page.getByRole('button', { name: '完成安装' }).click()
  await expect(page.getByText('安装已由服务确认完成', { exact: false })).toBeVisible({ timeout: 15000 })
  const complete = requests.find(item => item.path === '/api/setup/complete')
  expect(complete.body.mode).toBe('ADOPT')
  expect(complete.body.existingAdmin).toEqual({ username: 'existing-admin', password: PASSWORD })
  expect(complete.body.admin).toBeUndefined()
  expect(complete.headers['x-csrf-token']).toBe(CSRF)
  expect(complete.headers['idempotency-key']).toBeTruthy()
})

test('mocked CSRF rejection does not advance the database step', async ({ page }) => {
  const requests = await mockSetup(page, { checkStatus: 403 })
  await page.goto('/setup')
  await page.getByLabel('安装码', { exact: true }).fill(CODE)
  await page.getByRole('button', { name: '继续', exact: true }).click()
  await page.getByRole('button', { name: '检查数据库' }).click()
  await expect(page.getByText('数据库检查未完成')).toBeVisible()
  await expect(page.getByRole('button', { name: '下一步' })).toBeDisabled()
  expect(requests.find(item => item.path === '/api/setup/database/check').headers['x-csrf-token']).toBe(CSRF)
  expect(requests.some(item => item.path === '/api/setup/complete')).toBe(false)
})

test('mocked completion conflict requires a new database check', async ({ page }) => {
  const requests = await mockSetup(page, { completeStatus: 409 })
  await completeWizard(page)
  await expect(page.getByText('安装未开始')).toBeVisible()
  await page.getByRole('button', { name: '重新检查数据库' }).click()
  await expect(page.getByRole('heading', { name: '连接数据库' })).toBeVisible()
  await expect(page.getByRole('button', { name: '下一步' })).toBeDisabled()
  expect(requests.filter(item => item.path === '/api/setup/complete')).toHaveLength(1)
})

test('mocked applied response with a different active revision stays unconfirmed', async ({ page }) => {
  await mockSetup(page, { mismatch: true })
  await completeWizard(page)
  await expect(page.getByText('尚无法确认生效', { exact: true })).toBeVisible()
  await expect(page.getByText('安装已由服务确认完成', { exact: false })).toHaveCount(0)
})

test('mocked recovery rolls back only after explicit confirmation', async ({ page }) => {
  const requests = []
  let session = false
  await page.route('**/backend/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/backend', '')
    const method = request.method()
    requests.push({ path, method, headers: request.headers(), body: request.postData() ? request.postDataJSON() : null })
    let status = 200
    let body
    if (path === '/api/setup/status') body = ok({ state: 'RECOVERY_REQUIRED', recoveryRequired: true })
    else if (path === '/api/setup/recovery/session' && method === 'GET') {
      status = session ? 200 : 401
      body = session ? ok({ csrfToken: CSRF, operationId: 'failed-operation' }) : { code: '401', msg: 'unauthorized', data: {} }
    } else if (path === '/api/setup/recovery/session' && method === 'POST') {
      session = true
      body = ok({ csrfToken: CSRF, operationId: 'failed-operation' })
    } else if (path === '/api/setup/operations/failed-operation') {
      body = ok({ targetRevision: 'failed-revision', activeRevision: 'active-revision', previousRevision: 'previous-revision', canRollback: true, canRetry: true })
    } else if (path === '/api/setup/recovery/config') {
      body = ok({ roles: {}, database: { mode: 'MANAGED' }, expectedRevision: 'failed-revision' })
    } else if (path === '/api/setup/recovery/rollback') {
      status = 202
      body = { code: '202', msg: 'accepted', data: { operationId: 'rollback-operation' } }
    } else if (path === '/api/setup/operations/rollback-operation') {
      body = ok({ operationId: 'rollback-operation', status: 'APPLIED', targetRevision: 'rollback-revision', activeRevision: 'rollback-revision' })
    } else {
      status = 404
      body = { code: '404', msg: 'unexpected mock request', data: {} }
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  })

  await page.goto('/setup')
  await page.getByLabel('恢复码', { exact: true }).fill('mock-recovery-code')
  await page.getByRole('button', { name: '继续', exact: true }).click()
  await expect(page.getByRole('heading', { name: '恢复配置' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: '恢复配置' })).toBeVisible()
  expect(requests.filter(item => item.path === '/api/setup/recovery/session' && item.method === 'GET').length).toBeGreaterThanOrEqual(2)
  expect(requests.some(item => item.path === '/api/setup/recovery/rollback')).toBe(false)
  await page.getByRole('button', { name: '回滚', exact: true }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  expect(requests.some(item => item.path === '/api/setup/recovery/rollback')).toBe(false)
  await page.getByRole('button', { name: '回滚', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await expect(page.getByText('配置已由服务确认生效', { exact: false })).toBeVisible({ timeout: 15000 })
  const exchange = requests.find(item => item.path === '/api/setup/recovery/session' && item.method === 'POST')
  const rollbacks = requests.filter(item => item.path === '/api/setup/recovery/rollback')
  expect(exchange.body).toEqual({ code: 'mock-recovery-code' })
  expect(rollbacks).toHaveLength(1)
  expect(rollbacks[0].body).toEqual({ operationId: 'failed-operation', expectedActiveRevision: 'active-revision' })
  expect(rollbacks[0].headers['x-csrf-token']).toBe(CSRF)
  expect(rollbacks[0].headers['idempotency-key']).toBeTruthy()
})
