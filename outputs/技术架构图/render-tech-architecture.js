const path = require('path');
const { chromium } = require(path.resolve(__dirname, '../../ai-learning-frontend/node_modules/playwright'));

(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
  });
  const page = await browser.newPage({ viewport: { width: 1800, height: 1705 }, deviceScaleFactor: 1.5 });
  await page.goto(`file:///${path.join(__dirname, 'system-tech-architecture.html').replace(/\\/g, '/')}`);
  await page.screenshot({ path: path.join(__dirname, '系统技术架构图.png'), fullPage: true });
  await browser.close();
  process.stdout.write(path.join(__dirname, '系统技术架构图.png'));
})().catch((error) => {
  console.error(error);
  process.exit(1);
});
