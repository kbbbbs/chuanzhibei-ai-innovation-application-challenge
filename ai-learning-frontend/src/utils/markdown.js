import { protectMath } from './math.js'
import { resolveResourceUrl } from './backendUrl.js'

export function prepareMarkdown(text) {
  if (!text) return ''
  // Historical local images must not reopen a connection to the retired service.
  return String(text).replace(/!\[[^\]]*\]\(local:\/\/[^)]+\)/g, '[插图请查看原始资料]')
}

const escapeAttribute = value => String(value).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

export function renderMarkdown(text, { allowRemoteImages = true } = {}) {
  if (!text) return ''

  let html = prepareMarkdown(text).replace(/\x00/g, '\uFFFD')

  const blocks = []
  const stash = value => { blocks.push(value); return `\x00${blocks.length - 1}\x00` }
  // Preserve literal examples before interpreting dollar delimiters.
  html = html.replace(/```([^\n]*)\n([\s\S]*?)```/g, (_, lang, code) => stash(`<pre><code>${escapeAttribute(code.trim())}</code></pre>`))
  html = html.replace(/`([^`\n]+)`/g, (_, code) => stash(`<code>${escapeAttribute(code)}</code>`))
  html = protectMath(html, stash)

  // 1. images ![alt](url) — MUST process before HTML escaping
  html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (_, alt, url) => {
    if (!allowRemoteImages || !/^https?:\/\//i.test(url)) return `[${escapeAttribute(alt || '插图')}]`
    blocks.push(`<img src="${escapeAttribute(resolveResourceUrl(url))}" alt="${escapeAttribute(alt || '')}" style="max-width:100%">`)
    return `\x00${blocks.length - 1}\x00`
  })

  html = html
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')

  // 2. code blocks (protect first)
  html = html.replace(/```(\w*)\s*\n([\s\S]*?)```/g, (_, lang, code) => {
    blocks.push(`<pre><code>${code.trim()}</code></pre>`)
    return `\x00${blocks.length - 1}\x00`
  })

  // 3. inline: bold + code — do BEFORE line-level processing
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')

  // 4. headings
  html = html.replace(/^#### (.+)$/gm, '<h5>$1</h5>')
  html = html.replace(/^### (.+)$/gm, '<h4>$1</h4>')
  html = html.replace(/^## (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^# (.+)$/gm, '<h2>$1</h2>')

  // 5. HR
  html = html.replace(/^---$/gm, '<hr>')

  // 6. tables — pipe-delimited or tab-delimited (3+ columns)
  // Normalize tab-separated tables before Markdown parsing.
  const lines = html.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const tabs = lines[i].split('\t')
    if (tabs.length >= 3 && lines[i + 1] && lines[i + 1].split('\t').length >= 3) {
      const isHeader = i
      const header = tabs.map(c => c.trim())
      const bodyTabs = lines[i + 1].split('\t').map(c => c.trim())
      if (bodyTabs.length === header.length) {
        const sepRow = header.map(() => '---').join(' | ')
        lines[i] = header.join(' | ')
        lines[i + 1] = sepRow
        for (let j = i + 2; j < lines.length; j++) {
          const row = lines[j].split('\t')
          if (row.length === header.length) {
            lines[j] = row.map(c => c.trim()).join(' | ')
          } else {
            break
          }
        }
      }
    }
  }
  html = lines.join('\n')

  // pipe tables
  html = html.replace(/(^[^\n]+\|[^\n]+\n^[ \t|:-]+\n(?:^[^\n]*\|[^\n]*(?:\n|$))+)/gm, (raw) => {
    const rows = raw.trim().split('\n')
    const headerCells = rows[0].split('|').filter(c => c.trim()).map(c => `<th>${c.trim()}</th>`).join('')
    const bodyRows = rows.slice(2).map(line =>
      `<tr>${line.split('|').filter(c => c.trim()).map(c => `<td>${c.trim()}</td>`).join('')}</tr>`
    ).join('')
    blocks.push(`<table><thead><tr>${headerCells}</tr></thead><tbody>${bodyRows}</tbody></table>`)
    return `\x00${blocks.length - 1}\x00`
  })

  // 7. ordered lists — group contiguous numbered lines
  html = html.replace(/(?:^\d+\. .+\n?)+/gm, (raw) => {
    const start = raw.match(/^\d+/)?.[0] || '1'
    const items = raw.trim().split('\n').map(l => `<li>${l.replace(/^\d+\. /, '')}</li>`).join('')
    return `<ol start="${start}">${items}</ol>\n\n`
  })

  // 8. unordered lists — group contiguous */- lines
  html = html.replace(/(?:^[*-] .+\n?)+/gm, (raw) => {
    const items = raw.trim().split('\n').map(l => `<li>${l.replace(/^[*-] /, '')}</li>`).join('')
    return `<ul>${items}</ul>\n\n`
  })

  // 8. split on 2+ newlines, wrap paragraphs
  html = html
    .split(/\n{2,}/)
    .map(s => s.trim())
    .filter(Boolean)
    .map(s => {
      if (/^<(h[2-5]|ul|ol|table|pre|hr|p)\b/.test(s)) return s
      return '<p>' + s.replace(/\n/g, '<br>') + '</p>'
    })
    .join('')

  // 9. restore blocks
  // Tables can contain earlier protected formula/code blocks. Resolve in insertion order.
  for (let i = 0; i < blocks.length; i++) {
    blocks[i] = blocks[i].replace(/\x00(\d+)\x00/g, (_, nested) => +nested < i ? blocks[+nested] : '')
  }
  html = html.replace(/\x00(\d+)\x00/g, (_, i) => blocks[+i])

  return html
}
