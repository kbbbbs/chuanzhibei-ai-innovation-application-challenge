import { test, expect } from '@playwright/test'

const recommendationData = {
  course: [
    {
      id: 'course-201', type: 'course', title: '机器学习进阶', subtitle: '人工智能',
      reason: '与你最近学习的机器学习方向一致。', score: 91, targetUrl: '/courses/201',
      meta: { difficulty: 'intermediate', rating: 4.8 }
    }
  ],
  exercise: [
    {
      id: 'exercise-301', type: 'exercise', title: '梯度下降配套练习', subtitle: '机器学习进阶',
      reason: '用于巩固学习率与收敛知识点。', score: 88, targetUrl: '/recommendation?type=exercise',
      meta: { courseId: 201, difficulty: 'medium' }
    }
  ],
  paper: [
    {
      id: 'paper-401', type: 'paper', title: 'Learning Rate Schedules in Optimization', subtitle: 'ICML',
      reason: '与你近期学习的梯度下降方向相关。', score: 86, targetUrl: '/paper-search',
      meta: { year: 2025, citationCount: 320 }
    }
  ],
}

async function signInAs(page, roleCode = 'student') {
  await page.addInitScript(role => {
    localStorage.setItem('token', `${role}-token`)
    localStorage.setItem('userId', role === 'teacher' ? '2' : '3')
    localStorage.setItem('username', role)
    localStorage.setItem('nickname', role === 'teacher' ? '教师' : '学习者')
    localStorage.setItem('roleCode', role)
  }, roleCode)
}

async function mockRecommendations(page, options = {}) {
  await page.route('**/api/recommendations/my**', async route => {
    const type = new URL(route.request().url()).searchParams.get('type') || 'all'
    if (options.failType === type) {
      await route.fulfill({ status: 503, json: { code: '503', msg: 'unavailable' } })
      return
    }
    const items = options.empty ? [] : (recommendationData[type] || [])
    await route.fulfill({
      json: {
        code: '200',
        data: { items, counts: { [type]: items.length }, summary: '', profileTags: [] },
      },
    })
  })
}

test('homepage renders course, exercise and paper recommendations in separate sections', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await signInAs(page)
  await mockRecommendations(page)
  await page.goto('/home')

  const nav = page.getByRole('navigation', { name: '首页模块导航' })
  await expect(nav.getByRole('button')).toHaveCount(5)
  await expect(nav.getByRole('button', { name: 'EduLoop 智教云环首页' })).toBeVisible()
  await expect(nav.getByRole('button', { name: '学习推荐' })).toBeVisible()
  await expect(nav.getByRole('button', { name: '科研发现' })).toBeVisible()
  await expect(nav.getByRole('button', { name: '互动实验' })).toBeVisible()
  await expect(nav.getByRole('button', { name: '智能备课' })).toBeVisible()
  await expect(page.locator('.section-jump')).toHaveCount(0)

  await expect(page.getByTestId('course-recommendations')).toContainText('机器学习进阶')
  await expect(page.getByTestId('exercise-recommendations')).toContainText('梯度下降配套练习')
  await expect(page.getByTestId('paper-recommendations')).toContainText('Learning Rate Schedules in Optimization')

  await page.getByRole('tab', { name: '切换到科研发现' }).click()
  await expect(page.getByRole('heading', { name: '把最近所学，延伸为新的研究问题' })).toBeVisible()

  await nav.getByRole('button', { name: '科研发现', exact: true }).click()
  await expect(page).toHaveURL(/#home-research-discovery$/)
  await expect.poll(async () => Number.parseFloat(await nav.locator('.home-nav-indicator').evaluate(element => getComputedStyle(element).width))).toBeGreaterThan(20)

  const homeEntry = nav.getByRole('button', { name: 'EduLoop 智教云环首页' })
  await homeEntry.click()
  await expect(page).toHaveURL(/#home-top$/)
  await expect(homeEntry).toHaveAttribute('aria-current', 'page')
  await expect.poll(() => page.locator('.layout-main').evaluate(element => element.scrollTop)).toBeLessThan(2)

  const interactivePreview = page.getByTestId('home-interactive-preview')
  const slider = interactivePreview.getByRole('slider')
  await expect(slider).toHaveCount(1)
  const descentLine = interactivePreview.locator('.curve-svg polyline').nth(1)
  const beforePoints = await descentLine.getAttribute('points')
  await slider.press('ArrowRight')
  await expect.poll(() => descentLine.getAttribute('points')).not.toBe(beforePoints)
})

test('homepage keeps designed readiness states when recommendation data is empty or partially unavailable', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await signInAs(page)
  await mockRecommendations(page, { empty: true, failType: 'exercise' })
  await page.goto('/home')

  await expect(page.getByTestId('course-readiness')).toBeVisible()
  await expect(page.getByTestId('exercise-readiness')).toContainText('推荐服务暂时不可用')
  await expect(page.getByTestId('paper-readiness')).toBeVisible()
  await expect(page.getByTestId('course-recommendation-item')).toHaveCount(0)
  await expect(page.getByTestId('exercise-recommendation-item')).toHaveCount(0)

  const laneHeight = await page.locator('.recommendation-columns').evaluate(element => element.getBoundingClientRect().height)
  expect(laneHeight).toBeLessThan(700)
})

test('header notice entry preserves the notice destination for guests', async ({ page }) => {
  await page.goto('/home')

  await page.getByRole('button', { name: '通知', exact: true }).click()

  await expect(page).toHaveURL(/\/login\?redirect=\/notices$/)
})

test('smart prep cards route teachers to the resource generator', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await signInAs(page, 'teacher')
  await mockRecommendations(page, { empty: true })
  await page.goto('/home')

  const prepFlows = page.getByTestId('prep-flow')
  await expect(prepFlows).toHaveCount(3)
  await prepFlows.first().click()
  await expect(page).toHaveURL(/\/resource-gen$/)
  await expect(page.getByRole('heading', { name: '智能资源生成' })).toBeVisible()
})
