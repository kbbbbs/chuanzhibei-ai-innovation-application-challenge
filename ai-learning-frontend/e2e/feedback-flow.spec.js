import { test, expect } from '@playwright/test'
import { expectMessage, loginByUi } from './helpers.js'

test.describe('错误操作友好反馈', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login')
    await page.evaluate(() => localStorage.clear())
  })

  test('登录错误和必填校验会给出中文提示', async ({ page }) => {
    await page.goto('/login')
    await page.getByRole('button', { name: '登录' }).click()
    await expect(page.getByText('请输入用户名')).toBeVisible()
    await expect(page.getByText('请输入密码')).toBeVisible()

    await page.getByPlaceholder('请输入用户名').fill('admin')
    await page.getByPlaceholder('请输入密码').fill('wrong-password')
    await page.getByRole('button', { name: '登录' }).click()
    await expectMessage(page, 'error', '用户名或密码错误')
    await expect(page).toHaveURL(/\/login/)
  })

  test('管理端表单错误和不存在学生会给出可理解反馈', async ({ page }) => {
    await loginByUi(page, 'admin', '123456', { expectHomeHeading: false })

    await page.goto('/categories')
    await page.getByRole('button', { name: '新增分类' }).click()
    await page.locator('.el-dialog:visible').getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('请输入分类名称')).toBeVisible()
    await page.locator('.el-dialog:visible').getByRole('button', { name: '取消' }).click()

    await page.goto('/users')
    await page.getByRole('button', { name: '新增用户' }).click()
    await page.locator('.el-dialog:visible').getByRole('button', { name: '保存' }).click()
    await expect(page.getByText('请输入用户名')).toBeVisible()
    await expect(page.getByText('请输入密码')).toBeVisible()
    await expect(page.getByText('请输入昵称')).toBeVisible()
    await page.locator('.el-dialog:visible').getByRole('button', { name: '取消' }).click()

    await page.goto('/teaching-classes')
    const firstClassRow = page.locator('.el-table__body-wrapper tr:visible').first()
    await expect(firstClassRow).toBeVisible()
    await firstClassRow.getByRole('button', { name: '学生管理' }).click()
    await expect(page.locator('.el-dialog:visible')).toBeVisible()
    await page.locator('.el-dialog:visible input').first().fill(`missing_student_${Date.now()}`)
    await page.locator('.el-dialog:visible').getByRole('button', { name: '添加学生' }).click()
    await expectMessage(page, 'error', '未找到该学生')
  })
})
