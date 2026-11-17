export function renderWikiMarkdown(content) {
  if (!content) return ''
  return content
    // Quotes are escaped too: link targets end up inside an attribute.
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')
    .replace(/^### (.+)$/gm, '<h4>$1</h4>')
    .replace(/^## (.+)$/gm, '<h3>$1</h3>')
    .replace(/^# (.+)$/gm, '<h2>$1</h2>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    // Wiki content comes from documents and models; only http(s) targets become links.
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, text, href) => /^https?:\/\//i.test(href.trim())
      ? `<a href="${href.trim()}" target="_blank" rel="noopener noreferrer">${text}</a>`
      : text)
    .replace(/\[\[([^\]|]+)\|([^\]]+)\]\]/g, '<span class="wiki-link">$2</span>')
    .replace(/\[\[([^\]]+)\]\]/g, '<span class="wiki-link">$1</span>')
    .replace(/\n\n/g, '</p><p>')
    .replace(/\n/g, '<br>')
    .replace(/^/, '<p>').replace(/$/, '</p>')
}
