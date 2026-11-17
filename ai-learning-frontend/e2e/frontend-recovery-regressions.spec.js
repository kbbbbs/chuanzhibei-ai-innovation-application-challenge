import { test, expect } from '@playwright/test'

const ok = data => ({ code: '200', msg: 'ok', data })

test('recovery locks environment-owned database fields and rejects a stale save', async ({ page }) => {
  let session = false
  const saves = []
  await page.route('**/backend/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/backend', '')
    const method = request.method()
    let status = 200
    let body
    if (path === '/api/setup/status') body = ok({ state: 'RECOVERY_REQUIRED', recoveryRequired: true })
    else if (path === '/api/setup/recovery/session' && method === 'GET') {
      status = session ? 200 : 401
      body = session ? ok({ csrfToken: 'mock-csrf', operationId: 'failed-operation' }) : { code: '401', msg: 'unauthorized' }
    } else if (path === '/api/setup/recovery/session' && method === 'POST') {
      session = true
      body = ok({ csrfToken: 'mock-csrf', operationId: 'failed-operation' })
    } else if (path === '/api/setup/operations/failed-operation') {
      body = ok({ targetRevision: 'failed-revision', activeRevision: 'active-revision', canRetry: true })
    } else if (path === '/api/setup/recovery/config' && method === 'GET') {
      body = ok({ roles: {}, expectedRevision: 'failed-revision', database: {
        mode: 'EXTERNAL', host: 'db.example', port: 3306, database: 'learning', username: 'user',
        tls: 'REQUIRED', passwordConfigured: true, lockedFields: ['host', 'password'],
        source: { host: 'ENVIRONMENT', password: 'ENVIRONMENT' },
      } })
    } else if (path === '/api/setup/recovery/config' && method === 'PUT') {
      saves.push(request.postDataJSON())
      status = 409
      body = { code: '409', msg: 'mock stale revision', data: {} }
    } else {
      status = 404
      body = { code: '404', msg: 'unexpected mock request' }
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  })
  await page.goto('/setup')
  await page.getByLabel('恢复码', { exact: true }).fill('mock-recovery-code')
  await page.getByRole('button', { name: '继续', exact: true }).click()
  await page.getByText('同时修正数据库连接').click()
  await expect(page.locator('#db-host')).toBeDisabled()
  await expect(page.getByRole('radio', { name: '替换', exact: true })).toBeDisabled()
  await expect(page.getByText('由环境变量管理，此处不能修改', { exact: true })).toHaveCount(2)
  await page.locator('#db-name').fill('changed')
  await page.getByRole('button', { name: '保存修正' }).click()
  await expect(page.getByText('配置已发生变化')).toBeVisible()
  await expect(page.getByText('修正已保存，尚未生效', { exact: false })).toHaveCount(0)
  expect(saves).toHaveLength(1)
  expect(saves[0]).toMatchObject({ expectedRevision: 'failed-revision', database: { database: 'changed', passwordAction: 'KEEP' } })
})

for (const scenario of [
  { name: 'different operation', operationId: 'other-operation', targetRevision: 'pending-1', applied: false },
  { name: 'different target', operationId: 'expected-operation', targetRevision: 'old-revision', applied: false },
  { name: 'matching operation and target', operationId: 'expected-operation', targetRevision: 'pending-1', applied: true },
]) test(`${scenario.name} reports settings application correctly`, async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('token', 'mock-token')
    localStorage.setItem('roleCode', 'admin')
  })
  await page.route('**/backend/api/**', async route => {
    const path = new URL(route.request().url()).pathname.replace('/backend', '')
    let status = 200
    let body
    if (path === '/api/setup/status') body = ok({ state: 'READY' })
    else if (path === '/api/admin/settings/models') body = ok({
      editableRevision: 'editable-1', activeRevision: 'active-1', pendingRevision: 'pending-1',
      lifecycleSupported: true, roles: {},
    })
    else if (path === '/api/admin/models/status') body = ok([])
    else if (path === '/api/admin/settings/apply') {
      status = 202
      body = { code: '202', msg: 'accepted', data: { operationId: 'expected-operation', targetRevision: 'pending-1' } }
    } else if (path === '/api/admin/settings/operations/expected-operation') {
      body = ok({ operationId: scenario.operationId, status: 'APPLIED', targetRevision: scenario.targetRevision, activeRevision: scenario.targetRevision })
    } else {
      status = 404
      body = { code: '404', msg: 'unexpected mock request' }
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  })
  await page.goto('/settings/models')
  await page.getByRole('button', { name: '应用并重启服务' }).click()
  await page.getByRole('button', { name: '应用', exact: true }).click()
  if (scenario.applied) {
    await expect(page.getByText('配置已由服务确认生效', { exact: false })).toBeVisible()
  } else {
    await expect(page.getByText('配置已由服务确认生效', { exact: false })).toHaveCount(0)
    await expect(page.getByText('尚无法确认生效', { exact: true })).toBeVisible()
  }
})
