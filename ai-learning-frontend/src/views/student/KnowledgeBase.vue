<template>
  <div class="kb-page">
    <div class="page-header kb-page-header">
      <div>
        <h2>智能学伴</h2>
        <p>选择知识库后，可以查看课件、继续对话并检索知识片段，让 AI 基于课程资料回答你的问题。</p>
      </div>
      <div class="header-actions" v-if="selectedKb">
        <el-tag type="primary" effect="light">{{ selectedKb.name }}</el-tag>
      </div>
    </div>

    <div class="kb-layout">
      <aside class="kb-left">
        <section class="panel">
          <div class="panel-title clickable" @click="toggleSection('knowledgeBases')">
            <div>
              <span>知识库</span>
              <span class="panel-sub">选择一个知识库开始学习</span>
            </div>
            <div class="panel-title-right">
              <el-tag size="small" effect="plain">{{ kbs.length }} 个</el-tag>
              <el-icon class="toggle-icon"><ArrowDown v-if="sectionOpen.knowledgeBases" /><ArrowRight v-else /></el-icon>
            </div>
          </div>
          <div v-show="sectionOpen.knowledgeBases" v-loading="kbLoading" class="panel-body scroll-y">
            <div v-if="kbError" class="kb-error-state">
              <div class="kb-error-icon">!</div>
              <p>{{ kbError }}</p>
              <el-button type="primary" size="small" @click="loadKBs">重新连接</el-button>
            </div>
            <el-empty v-else-if="kbs.length === 0 && !kbLoading" description="暂无知识库" />
            <div v-for="kb in kbs" :key="kb.id"
              :class="['kb-item', { active: selectedKb?.id === kb.id }]"
              @click="selectKb(kb)">
              <div class="kb-item-main">
                <span class="kb-name">{{ kb.name }}</span>
                <span class="kb-desc">{{ kb.description || '当前知识库可用于问答、总结和知识检索。' }}</span>
              </div>
              <el-tag size="small" effect="plain">{{ kb.knowledge_count || 0 }} 份</el-tag>
            </div>
          </div>
        </section>

        <section class="panel" v-if="selectedKb">
          <div class="panel-title clickable" @click="toggleSection('files')">
            <div>
              <span>课件资料</span>
              <span class="panel-sub">{{ selectedKb.name }}</span>
            </div>
            <div class="panel-title-right">
              <el-button size="small" type="primary" link @click.stop="openGraph">知识图谱</el-button>
              <el-icon class="toggle-icon"><ArrowDown v-if="sectionOpen.files" /><ArrowRight v-else /></el-icon>
            </div>
          </div>
          <div v-show="sectionOpen.files" class="panel-body scroll-y" v-loading="filesLoading">
            <el-empty v-if="files.length === 0 && !filesLoading" description="暂无课件" />
            <div v-for="f in files" :key="f.id" class="file-item file-item-static">
              <span class="file-icon">{{ fileIcon(f) }}</span>
              <div class="file-info">
                <span class="file-name">{{ f.title || f.file_name || f.id }}</span>
                <span class="file-meta">{{ f.file_type || f.type || '未知类型' }}</span>
              </div>
            </div>
          </div>
        </section>

        <section class="panel" v-if="selectedKb">
          <div class="panel-title clickable" @click="toggleSection('graph')">
            <div>
              <span>知识图谱</span>
              <span class="panel-sub">查看当前知识库的结构预览</span>
            </div>
            <div class="panel-title-right">
              <el-button size="small" type="primary" link @click.stop="openGraph">查看完整图谱</el-button>
              <el-icon class="toggle-icon"><ArrowDown v-if="sectionOpen.graph" /><ArrowRight v-else /></el-icon>
            </div>
          </div>
          <div v-show="sectionOpen.graph" class="panel-body">
            <KnowledgeGraphPreview :kb-id="selectedKb.id" />
          </div>
        </section>

        <section class="panel flex-1" v-if="selectedKb">
          <div class="panel-title clickable" @click="toggleSection('sessions')">
            <div>
              <span>对话记录</span>
              <span class="panel-sub">继续之前的学习会话</span>
            </div>
            <div class="panel-title-right">
              <el-button size="small" type="primary" link @click.stop="newSession">+ 新建</el-button>
              <el-icon class="toggle-icon"><ArrowDown v-if="sectionOpen.sessions" /><ArrowRight v-else /></el-icon>
            </div>
          </div>
          <div v-show="sectionOpen.sessions" class="panel-body scroll-y">
            <el-empty v-if="sessions.length === 0" description="暂无对话">
              <el-button size="small" type="primary" @click="newSession">开始新对话</el-button>
            </el-empty>
            <div v-for="s in sessions" :key="s.id"
              :class="['session-item', { active: currentSessionId === s.id }]"
              @click="switchSession(s)">
              <div class="session-main">
                <span class="session-title">{{ s.title || s.id }}</span>
                <span class="session-meta">点击继续这段对话</span>
              </div>
              <el-button size="small" type="danger" :icon="Delete" circle @click.stop="removeSession(s.id)" />
            </div>
          </div>
        </section>
      </aside>

      <main class="kb-right">
        <section class="view-switcher panel" v-if="selectedKb">
          <div class="view-switcher-inner">
            <el-button :type="mainMode === 'chat' ? 'primary' : 'default'" round @click="setMainMode('chat')">智能问答</el-button>
            <el-button :type="mainMode === 'graph' ? 'primary' : 'default'" round @click="setMainMode('graph')">知识图谱</el-button>
            <el-button :type="mainMode === 'search' ? 'primary' : 'default'" round @click="setMainMode('search')">知识检索</el-button>
          </div>
        </section>

        <section class="chat-panel panel" v-if="mainMode === 'chat'">
          <div class="chat-header">
            <div>
              <span class="chat-title">智能学伴</span>
              <span class="panel-sub">{{ selectedKb ? '基于当前知识库进行问答、总结与讲解。' : '请先在左侧选择一个知识库。' }}</span>
            </div>
            <div class="chat-header-tags">
              <el-tag size="small" effect="plain">知识库 RAG</el-tag>
              <el-tag v-if="selectedKb" size="small" type="primary" effect="light">{{ selectedKb.name }}</el-tag>
            </div>
          </div>

          <div class="chat-msgs" ref="chatRef">
            <div v-if="messages.length === 0 && !chatLoading" class="chat-welcome">
              <div class="welcome-icon">📖</div>
              <h3>{{ selectedKb ? '开始向智能学伴提问' : '请选择知识库' }}</h3>
              <p class="welcome-text">{{ selectedKb ? '我会基于当前知识库内容回答问题、总结重点，并结合检索结果做讲解。' : '选择左侧知识库后，你就可以围绕课件内容继续对话与检索。' }}</p>
              <div class="hint-list" v-if="selectedKb">
                <span v-for="h in exampleQuestions" :key="h" class="hint-tag"
                  @click="inputText = h; sendMessage()">{{ h }}</span>
              </div>
            </div>

            <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
              <span class="msg-who">{{ msg.role === 'user' ? '我' : 'AI' }}</span>
              <div class="msg-bubble">
                <div class="msg-text">
                  <MdPreview :modelValue="proxyLocalUrls(msg.content)" previewTheme="default" />
                </div>
                <div v-if="msg.role === 'assistant' && msg.citations?.length" class="citation-box">
                  <div class="citation-title">引用来源</div>
                  <div v-for="citation in msg.citations" :key="citation.key" class="citation-item">
                    <div class="citation-head">
                      <span class="citation-name">{{ citationTitle(citation) }}</span>
                      <span v-if="formatParseScore(citation.scoreLabel)" class="citation-score">{{ formatParseScore(citation.scoreLabel) }}</span>
                    </div>
                    <div class="citation-excerpt">{{ citationExcerpt(citation) }}</div>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="chatLoading" class="msg assistant">
              <span class="msg-who">AI</span>
              <span class="msg-text dim typing-indicator"><span></span><span></span><span></span></span>
            </div>
          </div>

          <div class="chat-bar" v-if="selectedKb">
            <el-input
              v-model="inputText"
              type="textarea"
              :rows="2"
              resize="none"
              placeholder="输入你的问题，按 Enter 发送"
              :disabled="chatLoading"
              @keydown.enter.exact.prevent="sendMessage"
            />
            <div class="chat-actions">
              <span class="chat-tip">基于当前知识库回答</span>
              <el-button type="primary" :loading="chatLoading" @click="sendMessage">发送</el-button>
            </div>
          </div>
        </section>

        <section class="graph-main panel" v-else-if="mainMode === 'graph'">
          <div class="chat-header">
            <div>
              <span class="chat-title">知识图谱</span>
              <span class="panel-sub">{{ selectedKb ? '在智能学伴中查看当前知识库的图谱结构。' : '请先在左侧选择一个知识库。' }}</span>
            </div>
            <div class="chat-header-tags">
              <el-tag v-if="selectedKb" size="small" type="primary" effect="light">{{ selectedKb.name }}</el-tag>
              <el-button v-if="selectedKb" size="small" type="primary" link @click="openGraphFullscreen">全屏查看</el-button>
            </div>
          </div>
          <div class="graph-main-body" v-if="selectedKb">
            <KnowledgeGraphPreview :kb-id="selectedKb.id" class="graph-main-preview" />
          </div>
          <div v-else class="graph-main-empty">
            <el-empty description="请选择知识库查看图谱" />
          </div>
        </section>

        <section class="search-panel panel" v-else-if="selectedKb">
          <div class="panel-title clickable" @click="searchOpen = !searchOpen">
            <div>
              <span>知识检索</span>
              <span class="panel-sub">{{ searchOpen ? '收起结果面板' : '展开结果面板' }}</span>
            </div>
            <span class="search-sub">{{ searchTotal > 0 ? searchTotal + ' 条结果' : (searchOpen ? '收起' : '展开') }}</span>
          </div>
          <div v-show="searchOpen" class="panel-body">
            <el-input v-model="searchQuery" placeholder="搜索知识库内容…" @keyup.enter="doSearch" class="search-input">
              <template #append><el-button @click="doSearch" :loading="searchLoading" :icon="Search" /></template>
            </el-input>

            <div v-if="groupedSearchResults.length" class="search-groups">
              <div v-for="group in groupedSearchResults" :key="group.key" class="search-group">
                <div class="search-group-header">
                  <div>
                    <div class="search-group-title">{{ group.title }}</div>
                    <div class="search-group-meta">{{ group.items.length }} 个命中片段</div>
                  </div>
                  <el-tag size="small" type="info" effect="plain">最高 {{ group.topScore }}%</el-tag>
                </div>

                <div class="search-list">
                  <div
                    v-for="item in group.items"
                    :key="item.key"
                    class="search-hit"
                    @click="toggleHit(item.key)"
                  >
                    <div class="hit-header">
                      <div class="hit-header-main">
                        <span class="hit-title">{{ item.title }}</span>
                        <span class="hit-file" v-if="item.fileLabel">{{ item.fileLabel }}</span>
                      </div>
                      <el-tag size="small" type="info" effect="plain" v-if="item.scoreLabel" class="hit-score">{{ item.scoreLabel }}</el-tag>
                    </div>
                    <div class="hit-preview" v-if="expandedHit !== item.key" v-html="item.previewHtml"></div>
                    <div class="hit-full" v-else>
                      <MdPreview :modelValue="proxyLocalUrls(item.content)" previewTheme="default" />
                    </div>
                    <div class="hit-actions" v-if="expandedHit === item.key">
                      <el-button size="small" type="primary" :loading="chatLoading" @click.stop="askAbout(item.raw)">让学伴讲解</el-button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div v-else-if="searchLoading" class="search-empty">搜索中…</div>
            <el-empty v-else-if="searchQuery.trim()" description="未找到相关内容" />
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Search, ArrowDown, ArrowRight } from '@element-plus/icons-vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { useUserStore } from '@/store/user'
import * as kbApi from '@/api/knowledgeBase'
import { weKnoraStreamChat } from '@/api/weknora'
import KnowledgeGraphPreview from '@/components/knowledge/KnowledgeGraphPreview.vue'

function unwrapData(data) {
  while (data && typeof data === 'object' && !Array.isArray(data) && data.data !== undefined)
    data = data.data
  return data
}

const userStore = useUserStore()
const router = useRouter()
const userId = computed(() => userStore.userId || 'anonymous')

const kbs = ref([]); const selectedKb = ref(null); const kbLoading = ref(false); const kbError = ref('')
const files = ref([]); const filesLoading = ref(false)
const sessions = ref([]); const currentSessionId = ref(null)
const historyMap = reactive(loadHistories())
const messages = ref([])
const chatLoading = ref(false); const inputText = ref(''); const chatRef = ref(null)
const searchOpen = ref(false); const searchQuery = ref(''); const searchResults = ref([]); const searchLoading = ref(false); const searchTotal = ref(0)
const mainMode = ref('chat')
const sectionOpen = reactive({
  knowledgeBases: true,
  files: true,
  graph: true,
  sessions: true
})
const expandedHit = ref('')
const previewVisible = ref(false); const previewLoading = ref(false)
const previewTitle = ref(''); const previewText = ref('')
const previewFileTypeObj = ref({})
const pdfImages = ref([])
const currentImageIndex = ref(0)
const pdfLoadError = ref(false)
const pdfErrorMessage = ref('')

const isPdfFile = computed(() => {
  return previewFileTypeObj.value?.file_type?.toLowerCase().includes('pdf') || false
})

function mySessionKey() { return 'kb_my_sessions_' + userId.value }
function loadMySessions() { try { return JSON.parse(localStorage.getItem(mySessionKey()) || '[]') } catch { return [] } }
function saveMySessions(ids) { localStorage.setItem(mySessionKey(), JSON.stringify(ids)) }

function cleanTitle(r) {
  const t = r.knowledge_title || r.title || r.file_name || ''
  return t.replace(/\.\w+$/, '').replace(/^新建文件夹\//g, '').slice(0, 50) || '知识片段'
}

function cleanContent(r) {
  const c = r?.content || r?.text || r?.chunk_content || r?.chunk_text || ''
  if (!c || typeof c !== 'string') return ''

  try {
    let result = c
    result = result.replace(/<!--[\s\S]*?-->/g, '')
    result = result.replace(/\n{3,}/g, '\n\n')
    return result.trim()
  } catch (e) {
    console.warn('cleanContent error:', e)
    return String(c).trim()
  }
}

function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function highlightText(text, keyword) {
  const safeText = escapeHtml(text)
  const trimmed = keyword?.trim()
  if (!trimmed) return safeText
  const escaped = trimmed.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return safeText.replace(new RegExp(`(${escaped})`, 'ig'), '<mark>$1</mark>')
}

function buildSearchItem(raw, index) {
  const title = cleanTitle(raw)
  const content = cleanContent(raw)
  const fileLabel = raw.file_name || raw.knowledge_title || raw.title || ''
  const score = Number(raw.score || 0)
  const key = `${fileLabel || title}-${index}`
  return {
    key,
    raw,
    title,
    content,
    fileLabel: fileLabel && fileLabel !== title ? fileLabel : '',
    score,
    scoreLabel: score ? `${Math.round(score * 100)}%` : '',
    previewHtml: highlightText(`${content.slice(0, 180)}${content.length > 180 ? '…' : ''}`, searchQuery.value)
  }
}

const groupedSearchResults = computed(() => {
  const groups = new Map()
  searchResults.value.forEach((raw, index) => {
    const item = buildSearchItem(raw, index)
    const groupKey = item.fileLabel || item.title || '未命名资料'
    if (!groups.has(groupKey)) {
      groups.set(groupKey, {
        key: groupKey,
        title: groupKey,
        items: [],
        topScore: 0
      })
    }
    const group = groups.get(groupKey)
    group.items.push(item)
    group.topScore = Math.max(group.topScore, Math.round(item.score * 100))
  })
  return Array.from(groups.values())
})

function unwrapSearchList(data) {
  const unwrapped = unwrapData(data)
  if (Array.isArray(unwrapped)) return unwrapped
  return unwrapped?.results || unwrapped?.items || unwrapped?.list || unwrapped?.documents || unwrapped?.records || []
}

function toggleHit(key) {
  expandedHit.value = expandedHit.value === key ? '' : key
}

function proxyLocalUrls(text) {
  if (!text) return ''
  return text.replace(/local:\/\/(\d+)\//g, '/api/weknora/local-file/$1/')
}

function normalizeCitation(item, index = 0) {
  if (!item) return null
  const raw = typeof item === 'string' ? { content: item } : item
  const title = raw.title || raw.knowledge_title || raw.file_name || raw.document_name || raw.name || `引用 ${index + 1}`
  const excerpt = cleanContent(raw) || raw.chunk || raw.snippet || raw.excerpt || raw.content || raw.text || raw.chunk_content || raw.chunk_text || ''
  const score = Number(raw.score || raw.similarity || raw.relevance || 0)
  return {
    key: `${title}-${index}`,
    title,
    excerpt,
    score,
    scoreLabel: score ? `${Math.round(score * 100)}%` : '',
    knowledgeId: raw.knowledge_id || raw.knowledgeId || raw.document_id || raw.documentId || '',
    raw
  }
}

function normalizeCitations(payload) {
  const sourceLists = [
    payload?.citations,
    payload?.references,
    payload?.sources,
    payload?.search_result,
    payload?.search_results,
    payload?.results,
    payload?.chunks
  ]
  const list = sourceLists.find(Array.isArray) || []
  return list.map(normalizeCitation).filter(Boolean)
}

function mergeCitations(existing = [], incoming = []) {
  const merged = [...existing]
  const seen = new Set(existing.map(item => item.key))
  incoming.forEach(item => {
    if (!item || seen.has(item.key)) return
    seen.add(item.key)
    merged.push(item)
  })
  return merged
}

async function fillCitationFallback(question, message) {
  if (!selectedKb.value?.id || message.citations?.length) return
  try {
    const res = await kbApi.searchKB({
      knowledgeBaseIds: [selectedKb.value.id],
      query: question,
      limit: 3
    })
    if (res.code !== '200' || !res.data) return
    const searchData = unwrapData(res.data)
    const list = Array.isArray(searchData) ? searchData : (searchData?.results || [])
    message.citations = mergeCitations(message.citations, list.slice(0, 3).map(normalizeCitation).filter(Boolean))
  } catch {
    // ignore fallback failure
  }
}

function citationTitle(citation) {
  return citation?.title || '未命名资料'
}

function citationExcerpt(citation) {
  return citation?.excerpt || '暂无原文片段'
}

function formatParseScore(scoreLabel) {
  return scoreLabel || ''
}

function toggleSection(key) {
  sectionOpen[key] = !sectionOpen[key]
}

const exampleQuestions = ['请总结核心知识点', '重点和难点是什么', '给我出几道练习题']


function historyKey(id) { return 'kb_history_' + id }
function loadHistories() {
  try {
    const map = {}
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (k && k.startsWith('kb_history_')) { map[k.replace('kb_history_', '')] = JSON.parse(localStorage.getItem(k)) }
    }
    return map
  } catch { return {} }
}
function saveHistory(id) {
  if (id && historyMap[id] && historyMap[id].length > 0) localStorage.setItem(historyKey(id), JSON.stringify(historyMap[id]))
}

function fileIcon(f) {
  const t = (f.file_type || f.type || '').toLowerCase()
  if (t.includes('pdf')) return ''
  if (t.includes('doc') || t.includes('word')) return '📝'
  if (t.includes('ppt') || t.includes('pptx')) return ''
  if (t.includes('txt') || t.includes('md')) return '📃'
  return '📎'
}

async function getImageWithAuth(imagePath) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
  const token = localStorage.getItem('token') || ''

  let url = imagePath
  if (!imagePath.startsWith('http')) {
    url = (baseUrl.startsWith('http') ? baseUrl : '') + imagePath
  }

  try {
    const response = await fetch(url, {
      headers: {
        'Authorization': token ? `Bearer ${token}` : ''
      }
    })

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const blob = await response.blob()
    return URL.createObjectURL(blob)
  } catch (error) {
    console.error('Failed to load image:', error)
    throw error
  }
}

function handleImageError(e) {
  console.error('Image load error:', e)
  pdfLoadError.value = true
  pdfErrorMessage.value = '图片加载失败，可能是权限问题或文件不存在'
}

function prevImage() {
  if (currentImageIndex.value > 0) {
    currentImageIndex.value--
  }
}

function nextImage() {
  if (currentImageIndex.value < pdfImages.value.length - 1) {
    currentImageIndex.value++
  }
}

function openGraph() {
  if (!selectedKb.value) return
  mainMode.value = 'graph'
}

function openGraphFullscreen() {
  if (!selectedKb.value?.id) return
  router.push({ name: 'KnowledgeGraph', query: { kbId: selectedKb.value.id } })
}

function setMainMode(mode) {
  mainMode.value = mode
  if (mode === 'search') searchOpen.value = true
}

const loadKBs = async () => {
  kbLoading.value = true
  kbError.value = ''
  try {
    const res = await kbApi.listKnowledgeBases()
    if (res.code === '200' && res.data) {
      const data = unwrapData(res.data)
      kbs.value = Array.isArray(data) ? data : (data?.items || data?.list || [])
    } else {
      kbError.value = res.msg || '获取知识库列表失败'
    }
  } catch (err) {
    kbError.value = '知识库服务无法连接，请确认 WeKnora 已启动（端口 8091）'
    kbs.value = []
  } finally { kbLoading.value = false }
}

async function loadFiles() {
  if (!selectedKb.value) return
  filesLoading.value = true
  try {
    const res = await kbApi.listKnowledge(selectedKb.value.id)
    if (res.code === '200') {
      const data = unwrapData(res.data)
      files.value = Array.isArray(data) ? data : (data?.items || data?.list || data?.documents || [])
    }
  } finally { filesLoading.value = false }
}

const selectKb = async (kb) => {
  if (currentSessionId.value) saveHistory(currentSessionId.value)
  selectedKb.value = kb; currentSessionId.value = null; messages.value = []
  files.value = []
  loadFiles()
  const myIds = loadMySessions()
  try {
    const res = await kbApi.listSessions()
    if (res.code === '200' && res.data) {
      const all = unwrapData(res.data) || []
      sessions.value = all.filter(s => myIds.includes(s.id))
      if (sessions.value.length > 0) {
        currentSessionId.value = sessions.value[0].id
        messages.value = historyMap[sessions.value[0].id] || []
      }
    }
  } catch { sessions.value = [] }
  await nextTick()
  scrollBottom()
}

async function openFilePreview(f) {
  previewTitle.value = f.title || f.file_name || f.id
  previewFileTypeObj.value = f
  previewText.value = ''
  pdfImages.value = []
  currentImageIndex.value = 0
  pdfLoadError.value = false
  pdfErrorMessage.value = ''
  previewVisible.value = true
  previewLoading.value = true

  try {
    const res = await kbApi.getKnowledgeContent(selectedKb.value?.id, f.id)
    if (res && (res.code === '200' || res.code === 200)) {
      const data = unwrapData(res.data) || []
      const chunks = Array.isArray(data) ? data : (data?.chunks || data?.items || [])

      const imagePaths = []
      let hasTextContent = false

      chunks.forEach(chunk => {
        if (chunk.file_path && (
          chunk.file_path.toLowerCase().includes('.jpg') ||
          chunk.file_path.toLowerCase().includes('.jpeg') ||
          chunk.file_path.toLowerCase().includes('.png') ||
          chunk.file_path.toLowerCase().includes('page_')
        )) {
          imagePaths.push(chunk.file_path)
        } else if (chunk.content || chunk.text) {
          hasTextContent = true
        }
      })

      if (imagePaths.length > 0) {
        try {
          const loadedImages = []
          for (const imgPath of imagePaths) {
            try {
              const blobUrl = await getImageWithAuth(imgPath)
              loadedImages.push(blobUrl)
            } catch (err) {
              console.error(`Failed to load image: ${imgPath}`, err)
            }
          }

          if (loadedImages.length > 0) {
            pdfImages.value = loadedImages
            currentImageIndex.value = 0
            previewLoading.value = false
            return
          }
        } catch (err) {
          console.error('Failed to load images:', err)
          pdfLoadError.value = true
          pdfErrorMessage.value = '图片加载失败'
        } finally {
          previewLoading.value = false
        }
        return
      }

      if (hasTextContent) {
        previewText.value = chunks.map(c => cleanContent(c)).filter(Boolean).join('\n\n')
        previewLoading.value = false
        return
      }
    }

    pdfLoadError.value = true
    pdfErrorMessage.value = res?.msg || '获取知识库文档内容失败'
  } catch (err) {
    console.error('Get content error:', err)
    pdfLoadError.value = true
    pdfErrorMessage.value = err?.message || '获取知识库文档内容失败'
  } finally {
    previewLoading.value = false
  }
}

function downloadFile() {
  const knowledgeId = previewFileTypeObj.value?.id
  if (!knowledgeId) {
    ElMessage.warning('无法获取文件下载地址')
    return
  }

  kbApi.downloadKnowledgeFile(knowledgeId)
    .then(blob => {
      const blobUrl = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = blobUrl
      link.download = previewTitle.value
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      URL.revokeObjectURL(blobUrl)
    })
    .catch(err => {
      console.error('Download failed:', err)
      const fallbackUrl = previewFileTypeObj.value?.file_url
      if (err?.response?.status === 404 && fallbackUrl) {
        const baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'
        window.open(baseUrl.startsWith('http') ? baseUrl + fallbackUrl : fallbackUrl, '_blank')
        return
      }
      const message = err?.response?.data?.msg || err?.response?.data?.message || err?.message || '下载失败'
      ElMessage.error(message)
    })
}

const newSession = async () => {
  if (!selectedKb.value) return
  try {
    const res = await kbApi.createSession({ knowledgeBaseId: selectedKb.value.id, title: '新对话' })
    if (res.code === '200' && res.data) {
      const data = unwrapData(res.data); const id = data?.id || data?.session_id
      if (id) {
        if (currentSessionId.value) saveHistory(currentSessionId.value)
        currentSessionId.value = id; sessions.value.unshift({ id, title: '新对话' }); messages.value = []
        registerSession(id)
      }
    }
  } catch { /* skip */ }
}

function registerSession(id) { const ids = loadMySessions(); if (!ids.includes(id)) { ids.unshift(id); saveMySessions(ids) } }

const switchSession = (s) => {
  if (currentSessionId.value === s.id) return
  saveHistory(currentSessionId.value)
  currentSessionId.value = s.id; messages.value = historyMap[s.id] || []
}

const removeSession = async (id) => {
  try { await ElMessageBox.confirm('确定删除此对话？', '确认', { type: 'warning' }) } catch { return }
  try {
    await kbApi.deleteSession(id)
    sessions.value = sessions.value.filter(s => s.id !== id); delete historyMap[id]
    const ids = loadMySessions().filter(sid => sid !== id); saveMySessions(ids)
    if (currentSessionId.value === id) { currentSessionId.value = null; messages.value = [] }
  } catch { ElMessage.error('删除失败') }
}

async function ensureSession() {
  if (currentSessionId.value) return true
  try {
    const res = await kbApi.createSession({ knowledgeBaseId: selectedKb.value.id, title: '新对话' })
    if (res.code === '200' && res.data) {
      const data = unwrapData(res.data); const id = data?.id || data?.session_id
      if (id) { currentSessionId.value = id; sessions.value.unshift({ id, title: '新对话' }); registerSession(id); return true }
    }
  } catch { /* fall through */ }
  return false
}

const sendMessage = async () => {
  const q = inputText.value.trim(); if (!q || !selectedKb.value || chatLoading.value) return
  inputText.value = ''
  if (!(await ensureSession())) { ElMessage.error('创建对话失败'); return }

  messages.value.push({ role: 'user', content: q })
  const aiMessage = { role: 'assistant', content: '', citations: [] }
  messages.value.push(aiMessage)
  const aiIdx = messages.value.length - 1
  chatLoading.value = true; await nextTick(); scrollBottom()

  await new Promise((resolve, reject) => {
    let finished = false
    weKnoraStreamChat('knowledge', currentSessionId.value, q, selectedKb.value?.id ? [selectedKb.value.id] : [], {
      onMessage(parsed) {
        const payload = typeof parsed === 'string' ? { content: parsed, response_type: 'answer' } : parsed
        if (payload?.error) {
          if (!finished) {
            finished = true
            reject(new Error(payload.error))
          }
          return
        }
        const incomingCitations = normalizeCitations(payload)
        if (incomingCitations.length) {
          messages.value[aiIdx].citations = mergeCitations(messages.value[aiIdx].citations, incomingCitations)
        }
        if (payload?.response_type === 'answer' && payload.content) {
          messages.value[aiIdx].content += payload.content
        }
        scrollBottom()
      },
      onDone() {
        if (!finished) {
          finished = true
          resolve()
        }
      },
      onError(err) {
        if (!finished) {
          finished = true
          reject(new Error(err?.error || '获取知识库对话失败'))
        }
      }
    })
  }).then(async () => {
    await fillCitationFallback(q, messages.value[aiIdx])
    if (currentSessionId.value) { historyMap[currentSessionId.value] = [...messages.value]; saveHistory(currentSessionId.value) }
  }).catch(err => {
    messages.value[aiIdx].content = err?.message || '获取知识库对话失败'
    ElMessage.error(messages.value[aiIdx].content)
  }).finally(() => {
    chatLoading.value = false
  })
}

const scrollBottom = () => nextTick(() => { const el = chatRef.value; if (el) el.scrollTop = el.scrollHeight })

const doSearch = async () => {
  if (!searchQuery.value.trim() || !selectedKb.value) return
  searchLoading.value = true; expandedHit.value = ''
  try {
    const res = await kbApi.searchKB({
      knowledgeBaseIds: [selectedKb.value.id],
      query: searchQuery.value.trim(),
      limit: 12
    })
    if (res.code === '200' && res.data) {
      const list = unwrapSearchList(res.data)
      list.sort((a, b) => (b.score || 0) - (a.score || 0))
      searchResults.value = list
      searchTotal.value = list.length
    }
  } catch { searchResults.value = [] } finally { searchLoading.value = false }
}

const askAbout = (result) => {
  const ctx = cleanContent(result).slice(0, 500)
  inputText.value = `请根据以下资料帮我讲解：${ctx}`
  sendMessage()
}

function onIframeLoad() {
  console.log('PDF iframe loaded')
}

function onIframeError() {
  console.error('PDF iframe failed to load')
  pdfLoadError.value = true
  pdfErrorMessage.value = 'PDF加载失败，可能是权限问题或文件不存在'
}

onMounted(loadKBs)
</script>

<style scoped>
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - 140px);
  min-height: calc(100vh - 140px);
  overflow: hidden;
}

.kb-page-header p {
  margin: 6px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.kb-layout {
  display: grid;
  grid-template-columns: minmax(260px, 320px) minmax(0, 1fr);
  gap: 12px;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.kb-left {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.kb-right {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.kb-left > .panel {
  flex-shrink: 0;
}

.panel {
  background: var(--bg-elevated);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: var(--card-shadow);
  min-height: 0;
}

.panel.flex-1 {
  flex: 1 1 260px;
  display: flex;
  flex-direction: column;
  min-height: 220px;
}

.panel-title,
.chat-header {
  padding: 16px 18px;
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-color);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.panel-sub {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--text-muted);
}

.panel-title.clickable {
  cursor: pointer;
  user-select: none;
}

.panel-title-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toggle-icon {
  font-size: 14px;
  color: var(--text-muted);
  transition: transform 0.2s ease;
}

.panel-body {
  padding: 12px;
}

.panel-body.scroll-y {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.kb-left .panel:nth-child(2) .panel-body.scroll-y {
  max-height: 220px;
}

.kb-left .panel:nth-child(3) .panel-body {
  max-height: 260px;
  overflow: hidden;
}

.kb-left .panel:nth-child(3) :deep(.preview-shell) {
  min-height: 220px;
  max-height: 220px;
}

.kb-left .panel:nth-child(3) :deep(.graph-preview-container) {
  min-height: 0;
  height: 220px;
}

.kb-error-state {
  text-align: center;
  padding: 28px 16px;
}
.kb-error-icon {
  width: 48px; height: 48px;
  border-radius: 50%;
  background: #fef2f2;
  color: #ef4444;
  font-size: 24px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  margin: 0 auto 12px;
}
.kb-error-state p {
  color: #6b7280;
  font-size: 13px;
  margin-bottom: 16px;
  line-height: 1.6;
}

.kb-item {
  padding: 14px 16px;
  border-radius: 14px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  border: 1px solid transparent;
  transition: all .18s ease;
}

.kb-item + .kb-item {
  margin-top: 8px;
}

.kb-item:hover {
  background: var(--bg-subtle);
  border-color: var(--border-color);
}

.kb-item.active {
  background: var(--primary-color-soft);
  border-color: var(--primary-color-light);
}

.kb-item-main {
  flex: 1;
  min-width: 0;
}

.kb-name {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.kb-desc {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 8px;
  border-bottom: 1px solid var(--border-color);
}

.file-item:last-child {
  border-bottom: none;
}

.file-item-static {
  cursor: default;
}

.file-icon {
  font-size: 18px;
  flex-shrink: 0;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.session-item {
  padding: 12px 14px;
  border-radius: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  border: 1px solid transparent;
  transition: all .18s ease;
}

.session-item:hover {
  background: var(--bg-subtle);
  border-color: var(--border-color);
}

.session-item.active {
  background: var(--primary-color-soft);
  border-color: var(--primary-color-light);
}

.session-main {
  flex: 1;
  min-width: 0;
}

.session-title {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.view-switcher {
  flex-shrink: 0;
}

.view-switcher-inner {
  display: flex;
  gap: 10px;
  padding: 14px 16px;
  flex-wrap: wrap;
}

.graph-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.graph-main-body {
  flex: 1;
  min-height: 0;
  padding: 12px;
  overflow: hidden;
}

.graph-main-preview {
  height: 100%;
}

.graph-main-preview :deep(.graph-preview-card) {
  height: 100%;
}

.graph-main-preview :deep(.graph-preview-stage) {
  height: calc(100% - 52px);
  min-height: 420px;
}

.graph-main-preview :deep(.graph-preview-flow) {
  height: 100%;
}

.graph-main-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chat-title {
  display: block;
}

.chat-header-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chat-msgs {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: #f8fafc;
  min-height: 0;
}

.chat-welcome {
  text-align: center;
  padding: 28px 16px;
}

.welcome-icon {
  font-size: 34px;
  margin-bottom: 10px;
}

.chat-welcome h3 {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--text-primary);
}

.welcome-text {
  max-width: 520px;
  margin: 0 auto 14px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.hint-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}

.hint-tag {
  padding: 6px 14px;
  border: 1px solid var(--border-color);
  background: var(--bg-elevated);
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-primary);
  cursor: pointer;
  transition: all .18s ease;
}

.hint-tag:hover {
  border-color: var(--primary-color-light);
  background: var(--primary-color-soft);
  color: var(--primary-color);
}

.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  align-items: flex-start;
}

.msg.user {
  flex-direction: row-reverse;
}

.msg-who {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #4f46e5, #3b82f6);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.msg.user .msg-who {
  background: #334155;
}

.msg-text {
  max-width: 78%;
  padding: 12px 16px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.05);
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-primary);
  word-break: break-word;
  box-shadow: 0 1px 2px rgba(0,0,0,0.02);
}

.msg.user .msg-text {
  background: #fff;
  color: var(--text-primary);
}

.msg-text :deep(.md-editor-preview),
.msg-text :deep(.md-editor-preview-wrapper) {
  background: transparent;
  padding: 0;
}

.msg-text :deep(.md-editor-preview a) {
  color: var(--primary-color);
}

.msg-text.dim {
  color: var(--text-secondary);
}

.typing-indicator {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.typing-indicator span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--text-muted);
  animation: kb-bounce 1.4s infinite ease-in-out both;
}

.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

.chat-bar {
  padding: 10px 14px;
  border-top: 1px solid var(--border-color);
  background: var(--bg-elevated);
  flex-shrink: 0;
}

.chat-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.chat-tip {
  font-size: 12px;
  color: var(--text-muted);
}

.search-panel {
  background: var(--bg-elevated);
  flex-shrink: 0;
}

.search-panel .panel-body {
  max-height: 240px;
  overflow-y: auto;
}

.search-sub {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}

.search-input {
  margin-bottom: 10px;
}

.search-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.search-groups {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-group {
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 12px;
  background: color-mix(in srgb, var(--bg-elevated) 92%, white);
}

.search-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.search-group-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-primary);
}

.search-group-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
}

.search-hit {
  padding: 14px 12px;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  cursor: pointer;
  background: var(--bg-elevated);
  transition: all .18s ease;
}

.search-hit:hover {
  border-color: var(--primary-color-light);
  box-shadow: var(--shadow-xs);
}

.hit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.hit-header-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hit-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.hit-file {
  font-size: 12px;
  color: var(--text-muted);
}

.hit-score {
  flex-shrink: 0;
}

.hit-preview {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.hit-preview :deep(mark) {
  background: #fef3c7;
  color: #92400e;
  padding: 0 2px;
  border-radius: 4px;
}

.hit-full {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.hit-actions {
  margin-top: 10px;
}

.search-empty {
  text-align: center;
  padding: 24px;
  color: var(--text-muted);
}

@keyframes kb-bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

@media (max-width: 1100px) {
  .kb-layout {
    grid-template-columns: 1fr;
  }

  .kb-right,
  .chat-panel {
    min-height: 420px;
  }
}

@media (max-width: 768px) {
  .kb-page-header,
  .panel-title,
  .chat-header,
  .chat-actions,
  .hit-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .msg-text {
    max-width: calc(100% - 44px);
  }
}
</style>
