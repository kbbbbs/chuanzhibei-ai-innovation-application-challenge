import { test, expect } from '@playwright/test'

test('public teacher application and learning map render without console errors', async ({ page }) => {
  const consoleErrors = []
  page.on('console', msg => {
    if (msg.type() === 'error') consoleErrors.push(msg.text())
  })

  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin === 'http://localhost:8080') {
      await route.fulfill({ json: { code: '200', data: null, msg: 'mocked' } })
      return
    }
    await route.continue()
  })

  await page.route('**/teacher-applications', async route => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: { code: '200', msg: '申请已提交', data: { id: 1, username: 'teacher_demo', status: 0 } } })
    } else {
      await route.continue()
    }
  })

  await page.goto('/register')
  await page.getByLabel('用户名').fill('teacher_demo')
  await page.getByLabel('昵称').fill('教师 Demo')
  await page.getByLabel('密码').fill('secret123')
  await page.getByLabel('确认').fill('secret123')
  await page.getByText('教师入驻申请').click()
  await page.getByLabel('说明').fill('申请创建机器学习互动课程')
  await page.getByRole('button', { name: '注册' }).click()
  await expect(page.getByText('申请已提交，请等待管理员审核')).toBeVisible()

  await page.addInitScript(() => {
    localStorage.setItem('token', 'demo-token')
    localStorage.setItem('userId', '3')
    localStorage.setItem('username', 'student')
    localStorage.setItem('nickname', '学生')
    localStorage.setItem('roleCode', 'student')
  })
  await page.evaluate(() => {
    localStorage.setItem('token', 'demo-token')
    localStorage.setItem('userId', '3')
    localStorage.setItem('username', 'student')
    localStorage.setItem('nickname', '学生')
    localStorage.setItem('roleCode', 'student')
  })
  const course = {
    id: 901,
    title: '机器学习互动课本：从函数到梯度下降',
    description: '完整互动课本：函数机器、误差尺、梯度下降、分类边界、Python 实验、战争迷雾学习地图和闯关任务。',
    difficulty: 'beginner',
    rating: 4.9,
  }
  const chapter = { id: 9901, courseId: 901, title: '第一章：函数是一台机器', description: '用输入、输出、旋钮理解函数', sortOrder: 1 }
  const gradientPage = {
    title: '1.2 梯度下降滑杆实验',
    summary: '拖动学习率、运行 Python 代码，理解收敛和震荡。',
    blocks: [
      { type: 'markdown', title: '核心讲解', content: '## 核心讲解\n\n输入进入模型后得到预测，误差尺给出距离，梯度告诉旋钮应该往哪里调。学习率不是越大越好，过大会跨过最低点，过小会走得很慢。' },
      { type: 'experiment', id: 'e1', title: '梯度下降滑杆实验', description: '拖动学习率，观察小车如何逼近最低点。', kind: 'gradient', defaults: { learningRate: 0.12 } },
      { type: 'ide', language: 'python', title: 'Python：最小梯度下降', description: '改学习率，观察 x 如何靠近 0。', starterCode: "x = -4.0\nlr = 0.12\nfor step in range(3):\n    x = x - lr * 2 * x\n    print(step, round(x, 4))", expectedOutputHint: '应该看到 x 逐步接近 0。' },
      { type: 'checkpoint', title: '完成检查', items: ['我能解释学习率', '我能运行代码并读懂输出'] },
    ],
  }
  const lessonPage = lesson => lesson.id === 902
    ? gradientPage
    : { title: lesson.title, blocks: [{ type: 'markdown', title: '核心讲解', content: lesson.content }] }
  const lessons = [
    { id: 901, chapterId: 9901, title: '1.1 函数工厂：输入如何变成输出', resourceType: 'markdown', content: '函数是一台输入输出机器。', duration: 600, sortOrder: 1 },
    { id: 902, chapterId: 9901, title: '1.2 梯度下降滑杆实验', resourceType: 'interactive_json_builtin', content: JSON.stringify(gradientPage), duration: 620, sortOrder: 2 },
    { id: 903, chapterId: 9901, title: '1.3 Boss 关卡', resourceType: 'markdown', content: '解释学习率震荡。', duration: 480, sortOrder: 3 },
  ]
  const experience = {
    overview: {
      title: course.title,
      domain: '机器学习',
      tagline: '把模型、误差和梯度做成看得见的地图、曲线和闯关任务',
      totalLessons: 3,
      estimatedMinutes: 28,
      outcomes: ['能解释函数机器', '能拖动学习率观察收敛', '能在 IDE 中运行实验代码'],
      finalProject: '训练一个能解释自己的小模型',
    },
    path: [
      { id: 901, chapterTitle: chapter.title, title: lessons[0].title, type: 'article', progress: 100, unlocked: true, summary: '已解锁', duration: 600 },
      { id: 902, chapterTitle: chapter.title, title: lessons[1].title, type: 'article', progress: 0, unlocked: true, summary: '实验', duration: 620 },
      { id: 903, chapterTitle: chapter.title, title: lessons[2].title, type: 'quiz', progress: 0, unlocked: false, summary: '完成前置节点后解锁', duration: 480 },
    ],
    textbook: [{
      id: chapter.id,
      title: chapter.title,
      description: chapter.description,
      lessons: lessons.map(lesson => ({
        ...lesson,
        progress: lesson.id === 901 ? 100 : 0,
        unlocked: lesson.id !== 903,
        objectives: ['说清输入、模型、误差、更新之间的关系', '用图像解释学习率变化', '完成一次代码实验'],
        keyIdeas: ['可视化先于公式记忆', '学习率过大会震荡', '输出必须能被检查'],
        examples: ['小车下山找谷底', '函数机器 y=2x+1', '误差尺衡量预测偏差'],
        practiceTasks: ['拖动学习率滑杆', '运行 Python 示例', '写下震荡原因'],
        checkpoints: lesson.id === 902 ? ['我能解释学习率', '我能运行代码并读懂输出'] : [],
        content: lessonPage(lesson).blocks.find(block => block.type === 'markdown')?.content || lesson.content,
        page: lessonPage(lesson),
        blocks: lessonPage(lesson).blocks,
      })),
    }],
    experiments: [{ id: 'e1', lessonId: 902, title: '梯度下降滑杆实验', description: '拖动学习率，观察小车如何逼近最低点。', kind: 'gradient', defaults: { learningRate: 0.12 } }],
    challenges: [],
    codeLabs: [{ lessonId: 902, language: 'python', title: 'Python：最小梯度下降', description: '改学习率，观察 x 如何靠近 0。', starterCode: "x = -4.0\nlr = 0.12\nfor step in range(3):\n    x = x - lr * 2 * x\n    print(step, round(x, 4))", expectedOutputHint: '应该看到 x 逐步接近 0。' }],
  }

  await page.route('**/courses/901/experience**', async route => {
    await route.fulfill({ json: { code: '200', data: experience } })
  })
  await page.route('**/courses/901/chapters', async route => {
    await route.fulfill({ json: { code: '200', data: [chapter] } })
  })
  await page.route('**/chapters/9901/lessons', async route => {
    await route.fulfill({ json: { code: '200', data: lessons } })
  })
  await page.route('**/courses/901', async route => {
    await route.fulfill({ json: { code: '200', data: course } })
  })
  await page.route('**/learning-progress**', async route => {
    await route.fulfill({ json: { code: '200', data: { records: [{ lessonId: 901, progress: 100 }] } } })
  })
  await page.route('**/lessons/*/playback', async route => {
    await route.fulfill({ json: { code: '200', data: {
      status: 'PENDING',
    } } })
  })
  await page.route('**/code-lab/run', async route => {
    await route.fulfill({ json: { code: '200', data: { stdout: '0 -3.04\\n1 -2.3104\\n2 -1.7559', stderr: '', exitCode: 0 } } })
  })

  await page.goto('/learning-map/901?classId=1')
  await expect(page.getByText('Amazing Teaching / 学习路线')).toBeVisible()
  await expect(page.getByRole('heading', { name: course.title })).toBeVisible()
  await expect(page.getByText('可见学习路径')).toBeVisible()
  await expect(page.getByText('未知区域')).toBeVisible()
  await expect(page.getByRole('button', { name: /运行代码/ })).toHaveCount(0)

  await page.getByRole('button', { name: '继续学习' }).click()
  await expect(page).toHaveURL(/\/course-learn\/901/)
  await expect(page.getByRole('heading', { name: '1.2 梯度下降滑杆实验' })).toBeVisible()
  await expect(page.locator('.block-stack').getByRole('heading', { name: '核心讲解' }).first()).toBeVisible()
  await expect(page.locator('.interactive-lab-card').getByText('梯度下降滑杆实验')).toBeVisible()
  const codeLab = page.locator('.code-lab-card')
  await expect(codeLab.getByText('Python：最小梯度下降')).toBeVisible()
  await codeLab.getByRole('button', { name: '运行代码' }).click()
  await expect(codeLab.getByText('0 -3.04')).toBeVisible()

  expect(consoleErrors).toEqual([])
})

