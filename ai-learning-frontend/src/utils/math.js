import katex from 'katex'

const escape = value => String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
const escaped = (text, index) => {
  let count = 0
  for (let i = index - 1; i >= 0 && text[i] === '\\'; i--) count++
  return count % 2 === 1
}

// Returns safe HTML only. Raw source is retained on every rendering error.
export function renderFormula(latex, display = true) {
  try {
    if (!latex || latex.length > 12000) throw new Error('Formula limit')
    return katex.renderToString(latex, {
      displayMode: display, throwOnError: true, trust: false,
      maxExpand: 500, maxSize: 20, strict: 'ignore', output: 'htmlAndMathml',
    })
  } catch {
    return `<span class="math-render-error" title="公式暂不能排版，请对照原页核验">${escape(latex)}</span>`
  }
}

// Called after code spans/fences are protected. Math must be protected before Markdown escaping.
export function protectMath(text, stash) {
  let result = '', previous = 0
  for (let i = 0; i < text.length;) {
    if (escaped(text, i)) { i++; continue }
    let open, close, display = false, environment = false
    if (text.startsWith('$$', i)) { open = close = '$$'; display = true }
    else if (text.startsWith('\\[', i)) { open = '\\['; close = '\\]'; display = true }
    else if (text.startsWith('\\(', i)) { open = '\\('; close = '\\)' }
    else if (text[i] === '$' && text[i + 1] && !/\s/.test(text[i + 1]) && !/^\$\d[\d.,]*(?:\s|$)/.test(text.slice(i))) { open = close = '$' }
    else {
      const match = text.slice(i).match(/^\\begin\{(equation\*?|align\*?|aligned|gather\*?|gathered|multline\*?|split|cases|[pbBvV]?matrix|array)\}/)
      if (match) { open = match[0]; close = `\\end{${match[1]}}`; display = environment = true }
    }
    if (!open) { i++; continue }
    let stop = text.indexOf(close, i + open.length)
    while (stop >= 0 && escaped(text, stop)) stop = text.indexOf(close, stop + close.length)
    if (stop < 0) { i += open.length; continue }
    const end = stop + close.length
    const latex = environment ? text.slice(i, end) : text.slice(i + open.length, stop)
    result += text.slice(previous, i) + stash(renderFormula(latex, display))
    previous = i = end
  }
  return result + text.slice(previous)
}
