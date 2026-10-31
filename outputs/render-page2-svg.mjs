import { chromium } from '../ai-learning-frontend/node_modules/playwright/index.mjs'
import { pathToFileURL } from 'node:url'

const browser = await chromium.launch({ channel: 'chrome', headless: true })

try {
  const page = await browser.newPage({
    viewport: { width: 1300, height: 2100 },
    deviceScaleFactor: 1,
  })
  await page.goto(
    pathToFileURL('D:/ai-learning/outputs/agent-module-flow-page2-write.svg').href,
    { waitUntil: 'load' },
  )
  await page.locator('svg').screenshot({
    path: 'D:/ai-learning/outputs/agent-module-flow-page2-write.png',
    omitBackground: false,
  })
} finally {
  await browser.close()
}
