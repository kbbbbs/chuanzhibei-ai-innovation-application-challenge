<template>
  <div class="kb-page">
    <div class="kb-left">
      <!-- KB list -->
      <div class="panel">
        <div class="panel-title">📚 知识库</div>
        <div v-loading="kbLoading" class="panel-body">
          <div v-if="kbs.length === 0 && !kbLoading" class="empty-hint">暂无知识库</div>
          <div v-for="kb in kbs" :key="kb.id"
            :class="['kb-item', { active: selectedKb?.id === kb.id }]"
            @click="selectKb(kb)">
            <span class="kb-name">{{ kb.name }}</span>
          </div>
        </div>
      </div>

      <!-- Upload -->
      <div class="panel" v-if="selectedKb">
        <div class="panel-title">📄 上传资料</div>
        <div class="panel-body">
          <el-upload :auto-upload="false" :limit="1" accept=".pdf,.txt,.md,.doc,.docx" drag
            :on-change="e => uploadFile = e.raw" :show-file-list="false" class="upload-drag">
            <span>拖拽或点击上传</span>
          </el-upload>
          <div v-if="uploadFile" class="upload-bar">
            <el-tag closable @close="uploadFile=null">{{ uploadFile.name }}</el-tag>
            <el-button type="primary" size="small" :loading="uploading" @click="doUpload">上传</el-button>
          </div>
        </div>
      </div>

      <!-- Sessions -->
      <div class="panel flex-1" v-if="selectedKb">
        <div class="panel-title">
          💬 对话记录
          <el-button size="small" type="primary" link @click="newSession">+ 新建</el-button>
        </div>
        <div class="panel-body scroll-y">
          <div v-for="s in sessions" :key="s.id"
            :class="['session-item', { active: currentSessionId === s.id }]"
            @click="switchSession(s)">
            <span class="session-title">{{ s.title || s.id }}</span>
            <el-button size="small" type="danger" :icon="Delete" circle @click.stop="removeSession(s.id)" />
          </div>
          <div v-if="sessions.length===0" class="empty-hint">暂无对话</div>
        </div>
      </div>
    </div>

    <!-- Right: chat -->
    <div class="kb-right">
      <div class="chat-panel">
        <div class="panel-title">
          📖 智能学伴
          <span v-if="!selectedKb" class="sub">— 请选择知识库</span>
        </div>
        <div class="chat-msgs" ref="chatRef">
          <div v-if="messages.length === 0 && !chatLoading" class="chat-welcome">
            <div class="welcome-icon">📖</div>
            <div class="welcome-text">上传课件后问我任何问题吧！</div>
            <div class="hint-list">
              <span v-for="h in exampleQuestions" :key="h" class="hint-tag"
                @click="inputText = h; sendMessage()">{{ h }}</span>
            </div>
          </div>
          <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
            <span class="msg-who">{{ msg.role === 'user' ? '我' : '📖' }}</span>
            <div class="msg-text">{{ msg.content }}</div>
          </div>
          <div v-if="chatLoading" class="msg assistant"><span class="msg-who">📖</span><span class="msg-text dim">思考中…</span></div>
        </div>
        <div class="chat-bar" v-if="selectedKb">
          <el-input v-model="inputText" placeholder="输入问题…" @keyup.enter="sendMessage" :disabled="chatLoading" />
          <el-button type="primary" :loading="chatLoading" @click="sendMessage">发送</el-button>
        </div>
      </div>

      <!-- Search -->
      <div class="search-panel" v-if="selectedKb">
        <div class="panel-title clickable" @click="searchOpen = !searchOpen">
          🔍 知识检索 <span class="search-sub">{{ searchOpen ? '收起' : '展开' }}{{ searchTotal > 0 ? ' · '+searchTotal+'条' : '' }}</span>
        </div>
        <div v-show="searchOpen" class="panel-body">
          <el-input v-model="searchQuery" placeholder="搜索知识库内容…" @keyup.enter="doSearch" size="small" class="search-input">
            <template #append><el-button @click="doSearch" :loading="searchLoading" :icon="Search" /></template>
          </el-input>
          <div v-if="searchResults.length" class="search-list">
            <div v-for="(r, i) in searchResults" :key="i" class="search-hit" @click="expandedHit = expandedHit===i ? -1 : i">
              <div class="hit-header">
                <span class="hit-title">{{ cleanTitle(r) }}</span>
                <el-tag size="small" type="info" effect="plain" v-if="r.score" class="hit-score">{{ (r.score*100).toFixed(0) }}%</el-tag>
              </div>
              <div class="hit-preview" v-if="expandedHit !== i">{{ cleanContent(r).slice(0, 120) }}…</div>
              <div class="hit-full" v-else>{{ cleanContent(r) }}</div>
              <div class="hit-actions" v-if="expandedHit === i">
                <el-button size="small" type="primary" :loading="chatLoading" @click.stop="askAbout(r)">让学伴讲解</el-button>
              </div>
            </div>
          </div>
          <div v-else-if="searchLoading" style="text-align:center;padding:20px;color:#909399">搜索中…</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import * as kbApi from '@/api/knowledgeBase'

const userStore = useUserStore()
const userId = computed(() => userStore.userId || 'anonymous')

const kbs = ref([]); const selectedKb = ref(null); const kbLoading = ref(false)
const uploadFile = ref(null); const uploading = ref(false)
const sessions = ref([]); const currentSessionId = ref(null)
const historyMap = reactive(loadHistories())
const messages = ref([])
const chatLoading = ref(false); const inputText = ref(''); const chatRef = ref(null)
const searchOpen = ref(false); const searchQuery = ref(''); const searchResults = ref([]); const searchLoading = ref(false); const searchTotal = ref(0)
const expandedHit = ref(-1)

// Per-user session tracking
function mySessionKey() { return 'kb_my_sessions_' + userId.value }
function loadMySessions() {
  try { return JSON.parse(localStorage.getItem(mySessionKey()) || '[]') } catch { return [] }
}
function saveMySessions(ids) { localStorage.setItem(mySessionKey(), JSON.stringify(ids)) }

function cleanTitle(r) {
  const t = r.title || r.file_name || ''
  return t.replace(/\.\w+$/, '').slice(0, 40) || '知识片段'
}
function cleanContent(r) {
  const c = r.content || r.text || ''
  return c.replace(/<!--[^>]*-->/g, '').replace(/\n{3,}/g, '\n\n').replace(/\s+$/gm, '').trim()
}

const exampleQuestions = ['请总结核心知识点', '重点和难点是什么', '给我出几道练习题']

function historyKey(id) { return 'kb_history_' + id }
function loadHistories() {
  try {
    const map = {}
    for (let i = 0; i < localStorage.length; i++) {
      const k = localStorage.key(i)
      if (k && k.startsWith('kb_history_')) {
        map[k.replace('kb_history_', '')] = JSON.parse(localStorage.getItem(k))
      }
    }
    return map
  } catch { return {} }
}
function saveHistory(id) {
  if (id && historyMap[id] && historyMap[id].length > 0) {
    localStorage.setItem(historyKey(id), JSON.stringify(historyMap[id]))
  }
}

const loadKBs = async () => {
  kbLoading.value = true
  try {
    const res = await kbApi.listKnowledgeBases()
    if (res.code === '200' && res.data) kbs.value = Array.isArray(res.data) ? res.data : (res.data.data || [])
  } catch { kbs.value = [] } finally { kbLoading.value = false }
}

const selectKb = async (kb) => {
  if (currentSessionId.value) saveHistory(currentSessionId.value)
  selectedKb.value = kb; currentSessionId.value = null; messages.value = []
  const myIds = loadMySessions()
  try {
    const res = await kbApi.listSessions()
    if (res.code === '200' && res.data) {
      const all = Array.isArray(res.data) ? res.data : (res.data.data || [])
      sessions.value = all.filter(s => myIds.includes(s.id))
      if (sessions.value.length > 0) {
        const last = sessions.value[0]
        currentSessionId.value = last.id
        messages.value = historyMap[last.id] || []
      }
    }
  } catch { sessions.value = [] }
}

const doUpload = async () => {
  if (!uploadFile.value || !selectedKb.value) return
  uploading.value = true
  try {
    const res = await kbApi.uploadToKB(selectedKb.value.id, uploadFile.value)
    if (res.code === '200') { ElMessage.success('上传成功'); uploadFile.value = null }
    else ElMessage.error(res.msg || '上传失败')
  } catch { ElMessage.error('上传失败') } finally { uploading.value = false }
}

const newSession = async () => {
  if (!selectedKb.value) return
  try {
    const res = await kbApi.createSession({ knowledgeBaseId: selectedKb.value.id, title: '新对话' })
    if (res.code === '200' && res.data) {
      const id = res.data.id || (res.data.data && res.data.data.id) || res.data.session_id
      if (id) {
        if (currentSessionId.value) saveHistory(currentSessionId.value)
        currentSessionId.value = id; sessions.value.unshift({ id, title: '新对话' }); messages.value = []
        registerSession(id)
      } else ElMessage.error('创建对话失败')
    } else ElMessage.error(res.msg || '创建对话失败')
  } catch { ElMessage.error('创建对话失败') }
}

function registerSession(id) {
  const ids = loadMySessions()
  if (!ids.includes(id)) { ids.unshift(id); saveMySessions(ids) }
}

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

const sendMessage = async () => {
  const q = inputText.value.trim(); if (!q || !selectedKb.value || chatLoading.value) return
  inputText.value = ''
  // auto-create session if none
  if (!currentSessionId.value) {
    try {
      const title = '新对话'
      const res = await kbApi.createSession({ knowledgeBaseId: selectedKb.value.id, title })
      if (res.code === '200' && res.data) {
        const id = res.data.id || (res.data.data && res.data.data.id) || res.data.session_id
        if (id) {
          currentSessionId.value = id
          sessions.value.unshift({ id, title })
          registerSession(id)
        } else { ElMessage.error('创建对话失败'); return }
      } else { ElMessage.error('创建对话失败'); return }
    } catch { ElMessage.error('创建对话失败'); return }
  }

  messages.value.push({ role: 'user', content: q })
  const aiIdx = messages.value.length; messages.value.push({ role: 'assistant', content: '' })
  chatLoading.value = true; await nextTick(); scrollBottom()

  const url = `http://localhost:8080/api/weknora/knowledge-chat/${currentSessionId.value}?query=${encodeURIComponent(q)}`
  try {
    const resp = await fetch(url, { method: 'POST', headers: { 'Authorization': `Bearer ${localStorage.getItem('token') || ''}` } })
    const reader = resp.body.getReader(); const dec = new TextDecoder(); let buf = ''
    while (true) {
      const { done, value } = await reader.read(); if (done) break
      buf += dec.decode(value, { stream: true })
      const lines = buf.split('\n'); buf = lines.pop()
      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        const raw = line.substring(5).trim(); if (raw === '[DONE]') continue
        try {
          const j = JSON.parse(raw)
          if (j.response_type !== 'answer') continue
          if (j.content) messages.value[aiIdx].content += j.content
        } catch { /* skip */ }
      }
      scrollBottom()
    }
    if (currentSessionId.value) {
      historyMap[currentSessionId.value] = [...messages.value]
      saveHistory(currentSessionId.value)
    }
    // update session title based on conversation content
    const sid = currentSessionId.value
    const cur = sessions.value.find(s => s.id === sid)
    if (cur && cur.title === '新对话' && messages.value.length >= 2) {
      cur.title = q.length > 15 ? q.slice(0, 15) + '…' : q
    }
  } catch { if (!messages.value[aiIdx].content) messages.value[aiIdx].content = '回复中断' }
  finally { chatLoading.value = false }
}

const scrollBottom = () => nextTick(() => { const el = chatRef.value; if (el) el.scrollTop = el.scrollHeight })

const doSearch = async () => {
  if (!searchQuery.value.trim() || !selectedKb.value) return
  searchLoading.value = true; expandedHit.value = -1
  try {
    const res = await kbApi.searchKB({ knowledgeBaseIds: [selectedKb.value.id], query: searchQuery.value.trim() })
    if (res.code === '200' && res.data) {
      searchResults.value = Array.isArray(res.data) ? res.data : (res.data.data || res.data.results || [])
      searchTotal.value = searchResults.value.length
    }
  } catch { searchResults.value = [] } finally { searchLoading.value = false }
}

const askAbout = (result) => {
  const ctx = cleanContent(result).slice(0, 500)
  inputText.value = `请根据以下资料帮我讲解：${ctx}`
  sendMessage()
}

onMounted(loadKBs)
</script>

<style scoped>
.kb-page { display: flex; gap: 16px; height: calc(100vh - 140px); }
.kb-left { width: 320px; display: flex; flex-direction: column; gap: 8px; overflow: hidden; }
.kb-right { flex: 1; display: flex; flex-direction: column; gap: 8px; overflow: hidden; }
.panel { background: #fff; border-radius: 8px; border: 1px solid #ebeef5; overflow: hidden; }
.panel.flex-1 { flex: 1; display: flex; flex-direction: column; }
.panel-title { padding: 10px 14px; font-weight: 600; font-size: 14px; color: #303133; border-bottom: 1px solid #ebeef5; display: flex; justify-content: space-between; align-items: center; }
.panel-title .sub { font-weight: 400; font-size: 12px; color: #b0b0b0; }
.panel-title.clickable { cursor: pointer; user-select: none; }
.panel-body { padding: 8px; }
.panel-body.scroll-y { flex: 1; overflow-y: auto; }
.empty-hint { color: #c0c4cc; text-align: center; padding: 16px 0; font-size: 13px; }

/* KB items */
.kb-item { padding: 8px 10px; border-radius: 6px; cursor: pointer; }
.kb-item:hover { background: #f5f7fa; }
.kb-item.active { background: #ecf5ff; border-left: 3px solid #409eff; }
.kb-name { font-size: 13px; }

/* Upload */
.upload-drag { width: 100%; }
.upload-drag :deep(.el-upload) { width: 100%; }
.upload-drag :deep(.el-upload-dragger) { width: 100%; padding: 12px; }
.upload-bar { margin-top: 8px; display: flex; align-items: center; gap: 8px; }

/* Sessions */
.session-item { padding: 8px 10px; border-radius: 6px; cursor: pointer; display: flex; align-items: center; justify-content: space-between; margin-bottom: 2px; }
.session-item:hover { background: #f5f7fa; }
.session-item.active { background: #ecf5ff; }
.session-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }

/* Chat */
.chat-panel { flex: 1; display: flex; flex-direction: column; background: #fff; border-radius: 8px; border: 1px solid #ebeef5; overflow: hidden; }
.chat-msgs { flex: 1; overflow-y: auto; padding: 12px; background: #fbf9f5; }
.chat-welcome { text-align: center; padding: 40px 16px; }
.welcome-icon { font-size: 42px; margin-bottom: 10px; }
.welcome-text { font-size: 14px; color: #909399; margin-bottom: 16px; }
.hint-list { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.hint-tag { padding: 4px 12px; border: 1px solid #dcdfe6; border-radius: 14px; font-size: 12px; color: #606266; cursor: pointer; }
.hint-tag:hover { border-color: #409eff; color: #409eff; }

.msg { display: flex; gap: 8px; margin-bottom: 12px; align-items: flex-start; }
.msg.user { flex-direction: row-reverse; }
.msg-who { width: 28px; height: 28px; border-radius: 50%; background: #409eff; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; flex-shrink: 0; }
.msg.user .msg-who { background: #67c23a; }
.msg-text { max-width: 72%; padding: 8px 12px; border-radius: 10px; background: #fff; font-size: 14px; line-height: 1.7; color: #303133; white-space: pre-wrap; word-break: break-word; }
.msg.user .msg-text { background: #ecf5ff; }
.msg-text.dim { color: #909399; font-size: 13px; }

.chat-bar { display: flex; gap: 8px; padding: 10px 14px; border-top: 1px solid #ebeef5; }

/* Search */
.search-panel { background: #fff; border-radius: 8px; border: 1px solid #ebeef5; overflow: hidden; }
.search-sub { font-size:12px;color:#909399;font-weight:400;margin-left:4px }
.search-input { margin-bottom: 8px }
.search-list { max-height: 320px; overflow-y: auto; }
.search-hit { padding: 8px 0; border-bottom: 1px solid #f0f0f0; cursor: pointer; }
.search-hit:last-child { border-bottom: none; }
.search-hit:hover { background: #fbf9f5; margin: 0 -8px; padding: 8px; border-radius: 4px; }
.hit-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.hit-title { font-size: 13px; font-weight: 500; color: #303133; }
.hit-score { flex-shrink: 0; }
.hit-preview { font-size: 12px; color: #909399; line-height: 1.6; }
.hit-full { font-size: 12px; color: #606266; line-height: 1.6; white-space: pre-wrap; max-height: 200px; overflow-y: auto; }
.hit-actions { margin-top: 8px; }
</style>
