<template>
  <div class="teacher-kb-page">
    <!-- Left: KB Management Panel -->
    <div class="kb-left">
      <div class="panel">
        <div class="panel-title">
          📚 知识库管理
          <el-button type="primary" size="small" @click="openCreateDialog">新建</el-button>
        </div>
        <div class="panel-body scroll-y" v-loading="kbLoading">
          <div v-if="kbs.length === 0 && !kbLoading" class="empty-hint">暂无知识库，点击"新建"创建</div>
          <div v-for="kb in kbs" :key="kb.id"
            :class="['kb-item', { active: selectedKb?.id === kb.id }]"
            @click="selectKb(kb)">
            <div class="kb-item-left">
              <span class="kb-name">{{ kb.name }}</span>
              <span class="kb-desc" v-if="kb.description">{{ kb.description }}</span>
            </div>
            <span class="kb-count">{{ kb.knowledge_count || 0 }} 文档</span>
          </div>
        </div>
      </div>

      <!-- Files Panel -->
      <div class="panel" v-if="selectedKb">
        <div class="panel-title">
          📄 {{ selectedKb.name }} - 文件列表
          <div class="panel-title-actions">
            <el-button type="primary" size="small" @click="openEditDialog(selectedKb)">编辑</el-button>
            <el-upload :auto-upload="false" :limit="5" accept=".pdf,.txt,.md,.doc,.docx"
              :on-change="e => (uploadFile = e.raw)" :show-file-list="false">
              <el-button size="small">上传</el-button>
            </el-upload>
            <el-button size="small" type="danger" @click="handleDeleteKb(selectedKb)">删除KB</el-button>
          </div>
        </div>
        <div class="upload-bar" v-if="uploadFile">
          <el-tag closable @close="uploadFile=null">{{ uploadFile.name }}</el-tag>
          <el-button type="success" size="small" :loading="uploading" @click="doUpload">确认上传</el-button>
        </div>
        <div class="panel-body scroll-y" v-loading="filesLoading">
          <div v-if="files.length === 0 && !filesLoading" class="empty-hint">暂无文件</div>
          <div v-for="f in files" :key="f.id" class="file-item">
            <span class="file-name">{{ f.title || f.file_name || f.id }}</span>
            <span class="file-meta">{{ f.file_type || f.type || '-' }} · {{ formatSize(f.file_size) }}</span>
            <el-button size="small" type="danger" :icon="Delete" circle @click.stop="handleDeleteFile(f)" />
          </div>
        </div>
      </div>
    </div>

    <!-- Right: AI Chat -->
    <div class="kb-right">
      <AiChatWidget :inline="true" :key="kbs.length + (selectedKb?.id || '')" />
    </div>

    <!-- Edit KB Dialog -->
    <el-dialog v-model="editVisible" title="编辑知识库" width="500px">
      <el-form :model="editForm" ref="editRef" label-width="80px" :rules="editRules">
        <el-form-item label="名称" prop="name">
          <el-input v-model="editForm.name" placeholder="知识库名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="简要描述" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- Create KB Dialog -->
    <el-dialog v-model="createVisible" title="新建知识库" width="500px">
      <el-form :model="createForm" :rules="createRules" ref="createRef" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="createForm.name" placeholder="知识库名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="简要描述（可选）" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="doCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import AiChatWidget from '@/components/AiChatWidget.vue'
import * as kbApi from '@/api/knowledgeBase'

function unwrapData(data) {
  while (data && typeof data === 'object' && !Array.isArray(data) && data.data !== undefined)
    data = data.data
  return data
}

const kbs = ref([]); const kbLoading = ref(false)
const selectedKb = ref(null)
const files = ref([]); const filesLoading = ref(false)
const uploadFile = ref(null); const uploading = ref(false)

const createVisible = ref(false); const creating = ref(false)
const createForm = reactive({ name: '', description: '' })
const createRef = ref(null)
const createRules = { name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }] }

const editVisible = ref(false); const saving = ref(false)
const editForm = reactive({ id: '', name: '', description: '' })
const editRef = ref(null)
const editRules = { name: [{ required: true, message: '请输入知识库名称', trigger: 'blur' }] }

function formatTime(t) {
  if (!t) return '-'
  return t.slice(0, 19).replace('T', ' ')
}
function formatSize(bytes) {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

async function loadKBs() {
  kbLoading.value = true
  try {
    const res = await kbApi.listKnowledgeBases()
    if (res.code === '200') kbs.value = unwrapData(res.data) || []
  } finally { kbLoading.value = false }
}

function selectKb(kb) {
  selectedKb.value = kb
  uploadFile.value = null
  loadFiles()
}

async function loadFiles() {
  if (!selectedKb.value) return
  filesLoading.value = true
  try {
    const res = await kbApi.listKnowledge(selectedKb.value.id)
    if (res.code === '200') files.value = unwrapData(res.data) || []
  } finally { filesLoading.value = false }
}

async function doUpload() {
  if (!uploadFile.value || !selectedKb.value) return
  uploading.value = true
  try {
    const res = await kbApi.uploadToKB(selectedKb.value.id, uploadFile.value)
    if (res.code === '200') {
      ElMessage.success(res.msg || '上传成功')
      uploadFile.value = null
      await loadFiles()
      await loadKBs()
    } else ElMessage.error(res.msg || '上传失败')
  } finally { uploading.value = false }
}

function openCreateDialog() {
  createForm.name = ''; createForm.description = ''
  createVisible.value = true
}

async function doCreate() {
  const valid = await createRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    const res = await kbApi.createKnowledgeBase({ name: createForm.name, description: createForm.description })
    if (res.code === '200') {
      ElMessage.success('创建成功')
      createVisible.value = false
      await loadKBs()
    } else ElMessage.error(res.msg || '创建失败')
  } finally { creating.value = false }
}

function openEditDialog(kb) {
  editForm.id = kb.id; editForm.name = kb.name; editForm.description = kb.description || ''
  editVisible.value = true
}

async function doEdit() {
  const valid = await editRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const res = await kbApi.updateKnowledgeBase(editForm.id, { name: editForm.name, description: editForm.description })
    if (res.code === '200') {
      ElMessage.success('已保存')
      editVisible.value = false
      await loadKBs()
      if (selectedKb.value?.id === editForm.id) {
        selectedKb.value.name = editForm.name
        selectedKb.value.description = editForm.description
      }
    } else ElMessage.error(res.msg || '保存失败')
  } finally { saving.value = false }
}

async function handleDeleteKb(row) {
  try { await ElMessageBox.confirm(`确定删除知识库「${row.name}」？操作不可恢复。`, '确认', { type: 'warning' }) } catch { return }
  try {
    const res = await kbApi.deleteKnowledgeBase(row.id)
    if (res.code === '200') {
      ElMessage.success('已删除')
      if (selectedKb.value?.id === row.id) { selectedKb.value = null; files.value = [] }
      await loadKBs()
    } else ElMessage.error(res.msg || '删除失败')
  } catch { ElMessage.error('删除失败') }
}

async function handleDeleteFile(row) {
  try { await ElMessageBox.confirm(`确定删除「${row.title || row.file_name || row.id}」？`, '确认', { type: 'warning' }) } catch { return }
  try {
    const res = await kbApi.deleteKnowledge(row.id)
    if (res.code === '200') {
      ElMessage.success('已删除')
      await loadFiles()
      await loadKBs()
    } else ElMessage.error(res.msg || '删除失败')
  } catch { ElMessage.error('删除失败') }
}

onMounted(loadKBs)
</script>

<style scoped>
.teacher-kb-page { display: flex; gap: 16px; height: calc(100vh - 120px); }
.kb-left { width: 380px; display: flex; flex-direction: column; gap: 8px; overflow: hidden; flex-shrink: 0; }
.kb-right { flex: 1; min-width: 0; }
.panel { background: #fff; border-radius: 8px; border: 1px solid #ebeef5; overflow: hidden; display: flex; flex-direction: column; max-height: 50%; }
.panel:last-child { flex: 1; min-height: 0; }
.panel-title { padding: 10px 14px; font-weight: 600; font-size: 14px; color: #303133; border-bottom: 1px solid #ebeef5; display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-shrink: 0; }
.panel-title-actions { display: flex; gap: 6px; align-items: center; }
.panel-body.scroll-y { flex: 1; overflow-y: auto; padding: 8px; min-height: 0; }
.empty-hint { color: #c0c4cc; text-align: center; padding: 24px 0; font-size: 13px; }
.upload-bar { padding: 8px; display: flex; align-items: center; gap: 8px; border-bottom: 1px solid #ebeef5; }
.kb-item { padding: 10px 12px; border-radius: 6px; cursor: pointer; display: flex; justify-content: space-between; align-items: center; transition: background .15s; }
.kb-item:hover { background: #f5f7fa; }
.kb-item.active { background: #ecf5ff; }
.kb-item-left { flex: 1; min-width: 0; }
.kb-name { font-size: 13px; font-weight: 500; color: #303133; display: block; }
.kb-desc { font-size: 11px; color: #909399; display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 2px; }
.kb-count { font-size: 11px; color: #909399; background: #f0f0f0; padding: 2px 8px; border-radius: 10px; flex-shrink: 0; margin-left: 8px; }
.file-item { padding: 8px 0; display: flex; align-items: center; gap: 8px; border-bottom: 1px solid #f5f5f5; }
.file-item:last-child { border-bottom: none; }
.file-name { flex: 1; font-size: 13px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-meta { font-size: 11px; color: #b0b0b0; flex-shrink: 0; }
</style>
