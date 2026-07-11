export function renderMarkdown(text) {
  if (!text) return ''

  // proxy local:// image URLs (preserve port)
  let html = text.replace(/local:\/\/(\d+)\//g, '/api/weknora/local-file/$1/')

  const blocks = []

  // 1. images ![alt](url) — MUST process before HTML escaping
  html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (_, alt, url) => {
    blocks.push(`<img src="${url}" alt="${alt || ''}" style="max-width:100%">`)
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
  html = html.replace(/((?:^[^\n]+\|[^\n]+\n^[^\n]*\|[-:\s|]+\n(?:^[^\n]+\|[^\n]+\n?))+)/gm, (raw) => {
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
    const items = raw.trim().split('\n').map(l => `<li>${l.replace(/^\d+\. /, '')}</li>`).join('')
    return `<ol>${items}</ol>`
  })

  // 8. unordered lists — group contiguous */- lines
  html = html.replace(/(?:^[*-] .+\n?)+/gm, (raw) => {
    const items = raw.trim().split('\n').map(l => `<li>${l.replace(/^[*-] /, '')}</li>`).join('')
    return `<ul>${items}</ul>`
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
  html = html.replace(/\x00(\d+)\x00/g, (_, i) => blocks[+i])

  return html
}
