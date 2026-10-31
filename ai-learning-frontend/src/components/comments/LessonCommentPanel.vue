<template>
  <el-card class="comment-card" v-loading="loading">
    <template #header>
      <div class="comment-header">
        <span>课时评论</span>
        <el-button size="small" :icon="Refresh" title="刷新评论" @click="loadComments" />
      </div>
    </template>

    <div class="comment-compose">
      <el-input
        v-model="content"
        type="textarea"
        :rows="3"
        maxlength="1000"
        show-word-limit
        placeholder="写下你的问题或想法"
      />
      <div class="compose-actions">
        <el-button type="primary" size="small" :loading="submitting" @click="handleSubmit">发布</el-button>
      </div>
    </div>

    <el-empty v-if="!comments.length && !loading" description="暂无评论" />
    <div v-else class="comment-list">
      <div v-for="comment in comments" :key="comment.id" class="comment-item">
        <el-avatar :size="32" :src="resolveAvatar(comment.authorAvatar)">
          {{ avatarText(comment.authorName) }}
        </el-avatar>
        <div class="comment-main">
          <div class="comment-meta">
            <span class="comment-author">{{ comment.authorName || '匿名用户' }}</span>
            <span class="comment-time">{{ formatTime(comment.createdAt) }}</span>
            <el-button
              v-if="comment.canDelete"
              link
              type="danger"
              size="small"
              @click="handleDelete(comment)"
            >
              删除
            </el-button>
          </div>
          <div class="comment-content">{{ comment.content }}</div>
        </div>
      </div>
    </div>

    <el-pagination
      v-if="total > pageSize"
      class="comment-pagination"
      small
      background
      layout="prev, pager, next"
      :current-page="pageNum"
      :page-size="pageSize"
      :total="total"
      @current-change="handlePageChange"
    />
  </el-card>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { createLessonComment, deleteLessonComment, getLessonComments } from '@/api/comment'
import { getRecords, getTotal } from '@/utils/apiHelpers'

const props = defineProps({
  lessonId: {
    type: Number,
    required: true
  }
})

const loading = ref(false)
const submitting = ref(false)
const comments = ref([])
const content = ref('')
const pageNum = ref(1)
const pageSize = 10
const total = ref(0)
let loadSeq = 0

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080').replace(/\/$/, '')

const loadComments = async () => {
  const requestId = ++loadSeq
  loading.value = true
  try {
    const res = await getLessonComments(props.lessonId, {
      pageNum: pageNum.value,
      pageSize
    })
    if (requestId !== loadSeq) return
    if (res.code === '200') {
      comments.value = getRecords(res.data)
      total.value = getTotal(res.data)
    }
  } catch (e) {
    // 全局已提示
  } finally {
    if (requestId === loadSeq) {
      loading.value = false
    }
  }
}

const handleSubmit = async () => {
  const nextContent = content.value.trim()
  if (!nextContent) {
    ElMessage.warning('请输入评论内容')
    return
  }

  submitting.value = true
  try {
    const res = await createLessonComment(props.lessonId, { content: nextContent })
    if (res.code === '200') {
      ElMessage.success('评论已发布')
      content.value = ''
      pageNum.value = 1
      await loadComments()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (comment) => {
  try {
    await ElMessageBox.confirm('确认删除这条评论？', '删除评论', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    const res = await deleteLessonComment(comment.id)
    if (res.code === '200') {
      ElMessage.success('评论已删除')
      await loadComments()
    }
  } catch (e) {
    // 用户取消时无需提示
  }
}

const handlePageChange = (nextPage) => {
  pageNum.value = nextPage
  loadComments()
}

const avatarText = (name) => {
  return (name || '?').trim().slice(0, 1)
}

const resolveAvatar = (url) => {
  if (!url) return ''
  if (/^(https?:)?\/\//i.test(url) || /^(data|blob):/i.test(url)) {
    return url
  }
  if (url.startsWith('/')) {
    return `${apiBaseUrl}${url}`
  }
  return url
}

const formatTime = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

watch(
  () => props.lessonId,
  () => {
    pageNum.value = 1
    content.value = ''
    loadComments()
  },
  { immediate: true }
)
</script>

<style scoped>
.comment-card {
  margin-top: 16px;
}

.comment-header,
.comment-meta,
.compose-actions {
  display: flex;
  align-items: center;
}

.comment-header {
  justify-content: space-between;
  gap: 12px;
}

.comment-compose {
  margin-bottom: 14px;
}

.compose-actions {
  justify-content: flex-end;
  margin-top: 8px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.comment-item {
  display: flex;
  gap: 10px;
  padding-bottom: 14px;
  border-bottom: 1px solid #ebeef5;
}

.comment-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.comment-main {
  flex: 1;
  min-width: 0;
}

.comment-meta {
  gap: 8px;
  min-height: 24px;
}

.comment-author {
  color: #303133;
  font-weight: 600;
}

.comment-time {
  color: #909399;
  font-size: 12px;
}

.comment-content {
  margin-top: 4px;
  color: #303133;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-pagination {
  justify-content: center;
  margin-top: 16px;
}
</style>
