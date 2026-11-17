<template>
  <el-card class="note-card" v-loading="loading">
    <template #header>
      <div class="note-header">
        <span>学习笔记</span>
        <span class="autosave-status" :class="`is-${autosave.state.value}`" data-testid="note-save-status" aria-live="polite">{{ autosave.label.value }}</span>
        <div class="note-actions">
          <el-button v-if="hasVideo" size="small" :icon="Timer" title="插入时间点" @click="insertTimePoint" />
          <el-button size="small" :icon="Download" title="下载笔记" @click="handleDownload" />
          <el-button size="small" type="primary" :loading="autosave.state.value === 'saving'" @click="handleSave">{{ autosave.state.value === 'error' ? '重试保存' : '立即保存' }}</el-button>
        </div>
      </div>
    </template>

    <el-input v-model="title" class="note-title" />
    <AiNoteAssistant
      :course-id="courseId"
      :lesson-id="lessonId"
      :lesson-title="lessonTitle"
      :note-content="content"
      :video-summary-md="videoSummaryMd"
      :current-video-time="currentVideoTime"
      :has-video="hasVideo"
      @append-result="appendAiResult"
    />
    <MdEditor
      v-model="content"
      language="zh-CN"
      class="note-editor"
      :theme="resolvedTheme"
      :preview="false"
      :html-preview="false"
      :toolbars-exclude="noteEditorToolbarsExclude"
    />
  </el-card>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue'
import { Download, Timer } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import AiNoteAssistant from '@/components/ai/AiNoteAssistant.vue'
import { getLessonNote, saveLessonNote } from '@/api/note'
import { useTheme } from '@/composables/useTheme'
import { useAutosave } from '@/composables/useAutosave'

const props = defineProps({
  courseId: Number,
  lessonId: {
    type: Number,
    required: true
  },
  lessonTitle: {
    type: String,
    default: ''
  },
  currentVideoTime: {
    type: Number,
    default: 0
  },
  hasVideo: {
    type: Boolean,
    default: false
  },
  videoSummaryMd: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:content'])

const { resolvedTheme } = useTheme()

const loading = ref(false)
const title = ref('')
const content = ref('')
const noteEditorToolbarsExclude = ['previewOnly', 'htmlPreview', 'catalog', 'github']
let loadSeq = 0
let ready = false

const autosave = useAutosave({
  key: () => `eduloop:note:${props.lessonId}`,
  snapshot: () => ({ title: title.value, contentMd: content.value }),
  save: async () => {
    const res = await saveLessonNote(props.lessonId, {
      title: title.value,
      contentMd: content.value,
      lastVideoTime: Math.floor(props.currentVideoTime || 0)
    }, { silent: true })
    return res?.code === '200'
  }
})

const loadNote = async () => {
  const requestId = ++loadSeq
  ready = false
  loading.value = true
  try {
    const res = await getLessonNote(props.lessonId)
    if (requestId !== loadSeq) return
    if (res.code === '200') {
      title.value = res.data?.title || props.lessonTitle || '学习笔记'
      content.value = res.data?.contentMd || ''
    }
  } catch (e) {
    // 全局已提示；本机暂存的内容仍会恢复
  } finally {
    if (requestId === loadSeq) {
      // An unsent local copy is newer than the server note: restore it and send it.
      const local = autosave.readLocal()
      const restored = local && (local.contentMd !== content.value || local.title !== title.value)
      if (restored) {
        title.value = local.title || title.value
        content.value = local.contentMd ?? content.value
      }
      await nextTick()
      ready = true
      if (restored) autosave.touch()
      loading.value = false
    }
  }
}

const handleSave = async () => {
  autosave.touch()
  if (await autosave.flush()) ElMessage.success('笔记已保存')
}

// The file is built from the editor, so it always matches what is on screen, saved or not.
const handleDownload = async () => {
  try {
    const blob = new Blob([content.value || ''], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${sanitizeFileName(title.value || props.lessonTitle || 'study-note')}.md`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    setTimeout(() => URL.revokeObjectURL(url), 0)
  } catch (e) {
    // 全局已提示
  }
}

const insertTimePoint = () => {
  const line = `- [${formatTime(props.currentVideoTime)}] ${props.lessonTitle || ''}`.trim()
  content.value = content.value ? `${content.value}\n${line}` : line
}

const appendAiResult = (value) => {
  const text = value == null ? '' : String(value).trim()
  if (!text) return
  content.value = content.value ? `${content.value}\n\n${text}` : text
}

const formatTime = (seconds) => {
  const total = Math.max(0, Math.floor(seconds || 0))
  const minutes = Math.floor(total / 60)
  const rest = total % 60
  return `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
}

const sanitizeFileName = (value) => {
  return value.replace(/[\\/:*?"<>|\r\n]+/g, '_')
}

watch(
  () => props.lessonId,
  loadNote,
  { immediate: true }
)

watch(content, (value) => {
  emit('update:content', value)
})

watch([title, content], () => {
  if (ready) autosave.touch()
})
</script>

<style scoped>
.note-card {
  margin-bottom: 16px;
}

.note-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
}

.note-header,
.note-actions {
  display: flex;
  align-items: center;
}

.note-header {
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.note-header > span {
  white-space: nowrap;
}

.note-header > .autosave-status {
  margin-right: auto;
  color: var(--text-subtle);
  font-size: var(--text-xs);
  white-space: normal;
}

.autosave-status.is-offline,
.autosave-status.is-error {
  color: var(--caution);
}

.note-actions {
  gap: 6px;
  flex-wrap: wrap;
}

.note-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.note-title {
  margin-bottom: 10px;
}

.note-editor {
  height: clamp(420px, 58vh, 540px);
}
</style>
