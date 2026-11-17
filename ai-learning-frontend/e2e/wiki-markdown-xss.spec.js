import { test, expect } from '@playwright/test'
import { renderWikiMarkdown } from '../src/utils/wikiMarkdown.js'

test('wiki links keep only http(s) targets', () => {
  for (const target of ['javascript:alert(1)', ' JavaScript:alert(1)', 'data:text/html,<b>x</b>', 'vbscript:x']) {
    const html = renderWikiMarkdown(`[点我](${target})`)
    expect(html).not.toContain('<a ')
    expect(html).toContain('点我')
  }
})

test('wiki link targets cannot break out of the href attribute', () => {
  const html = renderWikiMarkdown('[x](https://example.com/" onmouseover="alert(1))')
  expect(html).not.toMatch(/"\s*onmouseover=/)
  expect(html).toContain('&quot;')
})

test('wiki links still render safe external links', () => {
  const html = renderWikiMarkdown('# 标题\n见 [论文](https://example.com/a?b=1&c=2) 与 [[节点|别名]]')
  expect(html).toContain('<h2>标题</h2>')
  expect(html).toContain('<a href="https://example.com/a?b=1&amp;c=2" target="_blank" rel="noopener noreferrer">论文</a>')
  expect(html).toContain('<span class="wiki-link">别名</span>')
})
