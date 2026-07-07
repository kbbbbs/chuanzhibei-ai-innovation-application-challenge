const TASK_TITLES = {
  summarize_note: '总结当前笔记',
  explain_selection: '解释选中内容',
  outline_from_video: '根据视频知识点生成提纲',
  quiz_from_note: '根据笔记生成练习题',
  quick_chat: '回答课时学习问题'
}

export function buildLessonAiPrompt({
  task = 'quick_chat',
  question = '',
  lessonTitle = '',
  videoSummaryMd = '',
  noteContent = '',
  selectedText = '',
  currentVideoTime = 0
} = {}) {
  const title = TASK_TITLES[task] || TASK_TITLES.quick_chat
  const sections = [
    `任务：${title}`,
    `当前课时：${lessonTitle || '未命名课时'}`,
    `当前视频时间：${formatTime(currentVideoTime)}`,
    `视频知识点总结：\n${clip(videoSummaryMd, 3000) || '暂无'}`,
    `当前 Markdown 笔记：\n${clip(noteContent, 6000) || '暂无'}`,
    `用户选中内容：\n${clip(selectedText, 1500) || '暂无'}`,
    `用户问题：${question || defaultQuestion(task)}`
  ]

  return [
    '你是在线学习平台中的课时 AI 助手。请优先依据下面提供的课时上下文回答，必要时再补充通用解释。',
    '回答请使用 Markdown，结构清晰，避免编造上下文中没有的事实。',
    ...sections
  ].join('\n\n')
}

function defaultQuestion(task) {
  const defaults = {
    summarize_note: '请提炼当前笔记的核心观点、遗漏点和下一步复习建议。',
    explain_selection: '请解释用户选中内容，并给出一个容易理解的例子。',
    outline_from_video: '请根据视频知识点总结生成适合写入笔记的学习提纲。',
    quiz_from_note: '请根据当前笔记生成 5 道练习题，并附简要答案。',
    quick_chat: '请结合课时上下文回答。'
  }
  return defaults[task] || defaults.quick_chat
}

function clip(value, maxLength) {
  const text = value == null ? '' : String(value).trim()
  if (text.length <= maxLength) {
    return text
  }
  return `${text.slice(0, maxLength)}\n\n（内容过长，已截断）`
}

function formatTime(seconds) {
  const total = Math.max(0, Math.floor(seconds || 0))
  const minutes = Math.floor(total / 60)
  const rest = total % 60
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}
