import { test, expect } from '@playwright/test'

// Controlled fixtures exercise browser behavior only; no real model service is contacted.
const TOKEN = 'mock-admin-token'
const SECRET = 'mock-replacement-secret'
const ok = data => ({ code: '200', msg: 'ok', data })

async function mockSettings(page, { conflict = false, locked = false, pending = false } = {}) {
  const requests = []
  let polls = 0
  const role = {
    enabled: true, baseUrl: 'https://example.com/v1', model: 'mock-model',
    timeoutSeconds: 30, secretConfigured: true, configuration: 'CONFIGURED_UNVERIFIED',
  }
  const settings = {
    editableRevision: 'editable-1', activeRevision: 'active-1',
    pendingRevision: pending ? 'pending-1' : null, lifecycleSupported: true,
    roles: {
      generation: locked ? { ...role, lockedFields: ['baseUrl', 'model', 'secret'], source: { baseUrl: 'ENVIRONMENT', model: 'ENVIRONMENT', secret: 'ENVIRONMENT' } } : { ...role },
      planning: { ...role }, embedding: { ...role, enabled: false }, ocr: { ...role, enabled: false },
    },
  }
  await page.addInitScript(token => {
    localStorage.setItem('token', token)
    localStorage.setItem('roleCode', 'admin')
  }, TOKEN)
  await page.route('**/backend/api/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/backend', '')
    const method = request.method()
    requests.push({ path, method, headers: request.headers(), body: request.postData() ? request.postDataJSON() : null })
    let status = 200
    let body
    if (path === '/api/setup/status') body = ok({ state: 'READY' })
    else if (path === '/api/admin/settings/models' && method === 'GET') body = ok(settings)
    else if (path === '/api/admin/models/status') body = ok([])
    else if (path === '/api/admin/settings/models' && method === 'PUT') {
      status = conflict ? 409 : 200
      body = conflict ? { code: '409', msg: 'mock conflict', data: {} } : ok({ pendingRevision: 'pending-1' })
    } else if (path === '/api/admin/settings/apply') {
      status = 202
      body = { code: '202', msg: 'accepted', data: { operationId: 'mock-operation' } }
    } else if (path === '/api/admin/settings/operations/mock-operation') {
      polls++
      status = polls === 1 ? 503 : 200
      body = status === 503 ? { code: '503', msg: 'restarting', data: {} } : ok({ operationId: 'mock-operation', status: 'APPLIED', targetRevision: 'pending-1', activeRevision: 'pending-1' })
    } else {
      status = 404
      body = { code: '404', msg: 'unexpected mock request', data: {} }
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
  })
  return requests
}

test('mocked settings submit KEEP, REPLACE, CLEAR and planning inheritance', async ({ page }) => {
  const requests = await mockSettings(page)
  await page.goto('/settings/models')
  const generation = page.getByRole('article').filter({ has: page.getByRole('heading', { name: '内容生成', exact: true }) })
  const planning = page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Agent 规划', exact: true }) })
  await generation.getByLabel('模型名称').fill('changed-model')
  await planning.getByText('整组继承内容生成的地址、模型和凭证').click()
  await page.getByRole('button', { name: '保存为待应用版本' }).click()
  await expect(page.getByText('已保存为待应用版本，运行中的配置尚未改变。')).toBeVisible()
  let saves = requests.filter(item => item.path === '/api/admin/settings/models' && item.method === 'PUT')
  expect(saves[0].headers.authorization).toBe(`Bearer ${TOKEN}`)
  expect(saves[0].body.expectedRevision).toBe('editable-1')
  expect(saves[0].body.roles.generation.secretAction).toBe('KEEP')
  expect(saves[0].body.roles.generation.secretValue).toBeUndefined()
  expect(saves[0].body.roles.planning).toMatchObject({ inheritFrom: 'generation', baseUrl: '', model: '', secretAction: 'KEEP' })

  await generation.getByText('替换', { exact: true }).click()
  await generation.getByPlaceholder('填写该服务自己的 API Key').fill(SECRET)
  await page.getByRole('button', { name: '保存为待应用版本' }).click()
  await expect.poll(() => requests.filter(item => item.path === '/api/admin/settings/models' && item.method === 'PUT').length).toBe(2)
  saves = requests.filter(item => item.path === '/api/admin/settings/models' && item.method === 'PUT')
  expect(saves[1].body.roles.generation).toMatchObject({ secretAction: 'REPLACE', secretValue: SECRET })
  await expect(page.getByText('已保存为待应用版本，运行中的配置尚未改变。')).toBeVisible()
  await generation.getByText('清除', { exact: true }).click()
  await page.getByRole('button', { name: '保存为待应用版本' }).click()
  await expect(page.getByText('清除凭证后该能力无法启用，请停用它或填写新凭证', { exact: false }).first()).toBeVisible()
  expect(requests.filter(item => item.path === '/api/admin/settings/models' && item.method === 'PUT')).toHaveLength(2)
  await generation.getByRole('switch', { name: '启用内容生成' }).locator('..').click()
  await page.getByRole('button', { name: '保存为待应用版本' }).click()
  await expect.poll(() => requests.filter(item => item.path === '/api/admin/settings/models' && item.method === 'PUT').length).toBe(3)
  saves = requests.filter(item => item.path === '/api/admin/settings/models' && item.method === 'PUT')
  expect(saves[2].body.roles.generation).toMatchObject({ enabled: false, secretAction: 'CLEAR' })
  expect(saves[2].body.roles.generation.secretValue).toBeUndefined()
})

test('mocked environment locks prevent editing managed fields', async ({ page }) => {
  await mockSettings(page, { locked: true })
  await page.goto('/settings/models')
  const generation = page.getByRole('article').filter({ has: page.getByRole('heading', { name: '内容生成', exact: true }) })
  await expect(generation.getByLabel('接口地址')).toBeDisabled()
  await expect(generation.getByLabel('模型名称')).toBeDisabled()
  await expect(generation.getByRole('radio', { name: '替换' })).toBeDisabled()
  await expect(generation.getByText('由环境变量管理，此处不能修改')).toHaveCount(3)
})

test('mocked draft probe can be cancelled while pending', async ({ page }) => {
  await mockSettings(page)
  let releaseProbe
  const pending = new Promise(resolve => { releaseProbe = resolve })
  let probeRequest
  await page.route('**/backend/api/admin/settings/models/probe', async route => {
    probeRequest = route.request()
    await pending
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ok({ checks: [] })) }).catch(() => {})
  })
  await page.goto('/settings/models')
  const generation = page.getByRole('article').filter({ has: page.getByRole('heading', { name: '内容生成', exact: true }) })
  await generation.getByRole('button', { name: '探测草稿' }).click()
  await expect(generation.getByRole('button', { name: '取消探测' })).toBeVisible()
  expect(probeRequest.postDataJSON().role).toBe('generation')
  await generation.getByRole('button', { name: '取消探测' }).click()
  await expect(generation.getByRole('button', { name: '探测草稿' })).toBeVisible()
  releaseProbe()
  await expect(generation.getByText('探测对象：当前草稿')).toHaveCount(0)
})

test('mocked save conflict shows reload and preserves authorization', async ({ page }) => {
  const requests = await mockSettings(page, { conflict: true })
  await page.goto('/settings/models')
  const generation = page.getByRole('article').filter({ has: page.getByRole('heading', { name: '内容生成', exact: true }) })
  await generation.getByLabel('模型名称').fill('changed-model')
  await page.getByRole('button', { name: '保存为待应用版本' }).click()
  await expect(page.getByText('配置已发生变化')).toBeVisible()
  const save = requests.find(item => item.path === '/api/admin/settings/models' && item.method === 'PUT')
  expect(save.headers.authorization).toBe(`Bearer ${TOKEN}`)
  expect(save.body.expectedRevision).toBe('editable-1')
  await page.getByRole('button', { name: '读取最新版本' }).click()
  await expect(generation.getByLabel('模型名称')).toHaveValue('mock-model')
})

test('mocked apply requires confirmation and waits through restart', async ({ page }) => {
  const requests = await mockSettings(page, { pending: true })
  await page.goto('/settings/models')
  await page.getByRole('button', { name: '应用并重启服务' }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  expect(requests.some(item => item.path === '/api/admin/settings/apply')).toBe(false)
  await page.getByRole('button', { name: '应用并重启服务' }).click()
  await page.getByRole('button', { name: '应用', exact: true }).click()
  await expect(page.getByText('配置已由服务确认生效', { exact: false })).toBeVisible({ timeout: 15000 })
  const apply = requests.find(item => item.path === '/api/admin/settings/apply')
  expect(apply.body).toEqual({ revision: 'pending-1', expectedActiveRevision: 'active-1' })
  expect(apply.headers.authorization).toBe(`Bearer ${TOKEN}`)
  expect(apply.headers['idempotency-key']).toBeTruthy()
  expect(requests.filter(item => item.path === '/api/admin/settings/operations/mock-operation').length).toBeGreaterThanOrEqual(2)
})
