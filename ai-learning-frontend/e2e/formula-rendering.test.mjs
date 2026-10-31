import test from 'node:test'
import assert from 'node:assert/strict'
import { renderMarkdown } from '../src/utils/markdown.js'

test('renders inline and display math without destroying braces, subscripts or ampersands', () => {
  const html = renderMarkdown(String.raw`Loss $x_i^2$.

$$\begin{bmatrix}1&2\\3&4\end{bmatrix}$$`)
  assert.match(html, /class="katex"/)
  assert.match(html, /katex-display/)
  assert.doesNotMatch(html, /katex-error/)
})
test('supports parenthesis and bracket delimiters with fraction markup', () => {
  assert.match(renderMarkdown(String.raw`\(x^2\) and \[\frac{a}{b}\]`), /mfrac/)
})
test('leaves code samples and escaped dollars literal', () => {
  const html = renderMarkdown('`$x$`\n\n```tex\n$$x$$\n```\n\nPrice \\$10')
  assert.doesNotMatch(html, /class="katex"/)
  assert.match(html, /<code>\$x\$/)
})
test('unrenderable or incomplete formula has visible source and no fabricated math', () => {
  const html = renderMarkdown(String.raw`$$\notARealMathCommand{x}$$`)
  assert.match(html, /math-render-error/)
  assert.match(html, /notARealMathCommand/)
  assert.doesNotMatch(renderMarkdown('$$x+'), /class="katex"/)
})
test('untrusted math cannot emit executable links or HTML', () => {
  const html = renderMarkdown(String.raw`$$\href{javascript:alert(1)}{x}$$

<img src=x onerror=alert(1)>`)
  assert.doesNotMatch(html, /<a[^>]+href="javascript:/i)
  assert.doesNotMatch(html, /<img[^>]+onerror=/i)
})

test('formulas and code inside Markdown tables restore their protected content', () => {
  const html = renderMarkdown('| Name | Value |\n| --- | --- |\n| Loss | $x_i^2$ |\n| Code | `$raw$` |')
  assert.match(html, /<table>/)
  assert.match(html, /class="katex"/)
  assert.match(html, /<td><code>\$raw\$<\/code><\/td>/)
  assert.doesNotMatch(html, /\x00/)
})

test('source-only rendering keeps formulas but does not load remote Markdown images', () => {
  const html = renderMarkdown(String.raw`题干 $x^2$ ![配图](https://untrusted.invalid/image.png)`, { allowRemoteImages: false })
  assert.match(html, /class="katex"/)
  assert.doesNotMatch(html, /<img\b/)
  assert.doesNotMatch(html, /https:\/\/untrusted/)
})

test('numbered question excerpts retain the source number and line breaks before options', () => {
  const html = renderMarkdown('2. 说明理由。\nA. 第一项\nB. 第二项')
  assert.match(html, /<ol start="2">/)
  assert.match(html, /A\. 第一项<br>B\. 第二项/)
})
