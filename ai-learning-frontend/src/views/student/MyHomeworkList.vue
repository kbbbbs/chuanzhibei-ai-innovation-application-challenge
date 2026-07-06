<template>
  <div class="my-homework">
    <el-card v-loading="loading">
      <template #header>
        <span>我的作业</span>
      </template>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="待完成" name="pending">
          <el-table :data="pendingHomework" border stripe>
            <template #empty><AppEmpty /></template>
            <el-table-column prop="title" label="作业标题" />
            <el-table-column label="截止时间" width="180">
              <template #default="{ row }">
                {{ row.deadline ? new Date(row.deadline).toLocaleString() : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="fullScore" label="总分" width="100" />
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button size="small" type="primary" @click="handleSubmit(row)">提交作业</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="已提交" name="submitted">
          <el-table :data="submittedHomework" border stripe>
            <template #empty><AppEmpty /></template>
            <el-table-column prop="title" label="作业标题" />
            <el-table-column label="提交时间" width="180">
              <template #default="{ row }">
                {{ row.submitTime ? new Date(row.submitTime).toLocaleString() : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="得分" width="100">
              <template #default="{ row }">
                {{ row.score != null ? `${row.score}/${row.fullScore}` : '待批改' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="isHomeworkGraded(row)" type="success">已批改</el-tag>
                <el-tag v-else type="warning">待批改</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" @click="handleView(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- Submit Dialog -->
    <el-dialog v-model="submitDialogVisible" title="提交作业" width="600px">
      <el-descriptions :column="1" border class="dialog-info">
        <el-descriptions-item label="作业标题">{{ currentHomework.title }}</el-descriptions-item>
        <el-descriptions-item label="作业内容">{{ currentHomework.content }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">
          {{ currentHomework.deadline ? new Date(currentHomework.deadline).toLocaleString() : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="附件" v-if="currentHomework.attachmentUrl">
          <el-link :href="currentHomework.attachmentUrl" target="_blank">查看附件</el-link>
        </el-descriptions-item>
      </el-descriptions>

      <el-form :model="submitForm" label-width="100px">
        <el-form-item label="作业内容">
          <el-input v-model="submitForm.content" type="textarea" :rows="6" placeholder="请输入作业内容" />
        </el-form-item>
        <el-form-item label="附件地址">
          <el-input v-model="submitForm.attachmentUrl" placeholder="选填：作业附件URL" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="submitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmitHomework">提交</el-button>
      </template>
    </el-dialog>

    <!-- View Dialog -->
    <el-dialog v-model="viewDialogVisible" title="作业详情" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="作业标题">{{ currentSubmission.title }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">
          {{ currentSubmission.submitTime ? new Date(currentSubmission.submitTime).toLocaleString() : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="我的答案">{{ currentSubmission.submitContent }}</el-descriptions-item>
        <el-descriptions-item label="得分" v-if="currentSubmission.score != null">
          {{ currentSubmission.score }} / {{ currentSubmission.fullScore }}
        </el-descriptions-item>
        <el-descriptions-item label="批改意见" v-if="currentSubmission.feedback">
          {{ currentSubmission.feedback }}
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import { currentUserId, formatLocalDateTime, getRecords } from '@/utils/apiHelpers'
import request from '@/utils/request'

const userStore = useUserStore()
const { confirmSubmit } = useConfirm()
const route = useRoute()
const router = useRouter()

const activeTab = ref('pending')
const pendingHomework = ref([])
const submittedHomework = ref([])

const submitDialogVisible = ref(false)
const viewDialogVisible = ref(false)
const currentHomework = ref({})
const currentSubmission = ref({})
const loading = ref(false)

const submitForm = reactive({
  content: '',
  attachmentUrl: ''
})

const isHomeworkGraded = (row) => {
  return row.gradeStatus === 1 || row.gradeStatus === 2 || row.score != null
}

const uniqueById = (items) => {
  const byId = new Map()
  for (const item of items) {
    if (item?.id != null) byId.set(item.id, item)
  }
  return Array.from(byId.values())
}

const loadHomework = async () => {
  loading.value = true
  try {
    const classRes = await request.get(`/class-students/student/${userStore.userId}`)
    if (classRes.code !== '200') return

    const classIds = getRecords(classRes.data)
      .map(c => Number(c.classId))
      .filter(id => Number.isFinite(id) && id > 0)

    if (classIds.length === 0) {
      pendingHomework.value = []
      submittedHomework.value = []
      return
    }

    const homeworkResults = await Promise.all(
      classIds.map(classId => request.get('/homeworks', {
        params: { classId, pageSize: 200 },
        silentError: true
      }).catch(() => null))
    )
    const allHomework = uniqueById(homeworkResults.flatMap(res => res?.code === '200' ? getRecords(res.data) : []))

    const subRes = await request.get('/homework-submits', {
      params: { studentId: userStore.userId, pageSize: 500 },
      silentError: true
    })
    const mySubmissions = subRes.code === '200' ? getRecords(subRes.data) : []
    const homeworkById = new Map(allHomework.map(hw => [hw.id, hw]))
    const submittedIds = new Set(mySubmissions.map(s => s.homeworkId))

    pendingHomework.value = allHomework.filter(hw => !submittedIds.has(hw.id) && hw.status === 1)
    submittedHomework.value = mySubmissions.map(sub => {
      const hw = homeworkById.get(sub.homeworkId)
      return { ...sub, title: hw?.title || sub.title || `作业${sub.homeworkId}`, fullScore: hw?.fullScore }
    })
    openHomeworkFromQuery()
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
  }
}

const handleSubmit = (row) => {
  currentHomework.value = row
  submitForm.content = ''
  submitForm.attachmentUrl = ''
  submitDialogVisible.value = true
}

const openHomeworkFromQuery = () => {
  const homeworkId = Number(route.query.homeworkId)
  if (!Number.isFinite(homeworkId) || homeworkId <= 0 || submitDialogVisible.value) return
  const homework = pendingHomework.value.find(item => item.id === homeworkId)
  if (!homework) return
  activeTab.value = 'pending'
  handleSubmit(homework)
}

const handleSubmitHomework = async () => {
  if (!submitForm.content) {
    ElMessage.warning('请输入作业内容')
    return
  }

  const confirmed = await confirmSubmit('确认提交作业？')
  if (!confirmed) return

  loading.value = true
  try {
    const data = {
      homeworkId: currentHomework.value.id,
      studentId: currentUserId(userStore),
      submitContent: submitForm.content,
      attachmentUrl: submitForm.attachmentUrl,
      submitTime: formatLocalDateTime(new Date()),
      gradeStatus: 0
    }

    const res = await request.post('/homework-submits', data)
    if (res.code === '200') {
      ElMessage.success('提交成功')
      submitDialogVisible.value = false
      if (route.query.homeworkId) {
        router.replace({ path: route.path, query: {} })
      }
      loadHomework()
    }
  } catch (e) {
    // 全局异常已由 request.js 提示
  } finally {
    loading.value = false
  }
}

const handleView = (row) => {
  currentSubmission.value = row
  viewDialogVisible.value = true
}

onMounted(() => {
  loadHomework()
})

watch(() => route.query.homeworkId, () => {
  openHomeworkFromQuery()
})
</script>

<style scoped>
.dialog-info {
  margin-bottom: 20px;
}
</style>
