import { chromium } from '../ai-learning-frontend/node_modules/playwright/index.mjs'
import { writeFile } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'
import path from 'node:path'

const jobs = [
  ['agent-flow-page1-render.html', 'agent-module-flow-page1.svg', 'agent-module-flow-page1.png'],
  ['agent-flow-page2-render.html', 'agent-module-flow-page2-write.svg', 'agent-module-flow-page2-write.png'],
]

const baseDir = path.resolve('outputs')
const browser = await chromium.launch({ channel: 'chrome', headless: true })

try {
  for (const [sourceName, svgName, pngName] of jobs) {
    const page = await browser.newPage({ viewport: { width: 2600, height: 2200 }, deviceScaleFactor: 1 })
    await page.goto(pathToFileURL(path.join(baseDir, sourceName)).href, { waitUntil: 'load' })
    const diagram = page.locator('.mermaid svg')
    await diagram.waitFor({ state: 'visible', timeout: 60000 })
    await page.waitForFunction(() => {
      const nodes = document.querySelector('.mermaid svg g.nodes')
      return nodes && nodes.children.length > 0
    }, { timeout: 60000 })
    await page.waitForTimeout(300)
    const svg = await diagram.evaluate((element) => {
      element.setAttribute('xmlns', 'http://www.w3.org/2000/svg')
      element.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink')
      const box = element.getBBox()
      element.setAttribute('viewBox', `${box.x - 12} ${box.y - 12} ${box.width + 24} ${box.height + 24}`)
      element.setAttribute('width', String(Math.ceil(box.width + 24)))
      element.setAttribute('height', String(Math.ceil(box.height + 24)))
      element.style.maxWidth = 'none'
      element.style.background = '#ffffff'
      return element.outerHTML
    })
    await writeFile(path.join(baseDir, svgName), svg, 'utf8')
    await diagram.screenshot({ path: path.join(baseDir, pngName), omitBackground: false })
    await page.close()
  }
} finally {
  await browser.close()
}
