const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const sharp = require('sharp');

const outputDir = __dirname;
const sourcePath = path.join(outputDir, '功能架构图.mmd');
const svgPath = path.join(outputDir, '功能架构图.svg');
const pngPath = path.join(outputDir, '功能架构图.png');

(async () => {
  const source = fs.readFileSync(sourcePath, 'utf8');
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
  });
  const page = await browser.newPage({
    viewport: { width: 1500, height: 1000 },
    deviceScaleFactor: 2
  });

  await page.setContent(`<!doctype html>
    <html lang="zh-CN">
      <head><meta charset="utf-8"></head>
      <body><div id="diagram"></div></body>
    </html>`);
  await page.addScriptTag({
    url: 'https://cdn.jsdelivr.net/npm/mermaid@11.12.0/dist/mermaid.min.js'
  });

  const svg = await page.evaluate(async (definition) => {
    mermaid.initialize({ startOnLoad: false, securityLevel: 'loose' });
    const rendered = await mermaid.render('functionalArchitecture', definition);
    return rendered.svg;
  }, source);

  fs.writeFileSync(svgPath, svg, 'utf8');

  await page.setContent(`<!doctype html>
    <html lang="zh-CN">
      <head>
        <meta charset="utf-8">
        <style>
          html, body { margin: 0; padding: 0; background: #ffffff; }
          svg { display: block; }
        </style>
      </head>
      <body>${svg}</body>
    </html>`);
  await page.locator('svg').screenshot({ path: pngPath });
  await browser.close();

  const metadata = await sharp(pngPath).metadata();
  process.stdout.write(JSON.stringify({
    svgPath,
    pngPath,
    width: metadata.width,
    height: metadata.height
  }, null, 2));
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
