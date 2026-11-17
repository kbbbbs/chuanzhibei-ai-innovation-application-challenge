<template>
  <div class="homework-submissions">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>作业提交列表：{{ homework?.title }}</span>
          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>

      <el-table :data="submissions" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="学生" width="150">
          <template #default="{ row }">
            学生{{ row.studentId }}
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="180">
          <template #default="{ row }">
            {{ row.submitTime ? new Date(row.submitTime).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="submitContent" label="提交内容" show-overflow-tooltip />
        <el-table-column label="附件" width="90">
          <template #default="{ row }">
            <el-link v-if="row.attachmentUrl" :href="resolveResourceUrl(row.attachmentUrl)" target="_blank" type="primary">查看</el-link>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="100">
          <template #default="{ row }">
            {{ row.score != null ? row.score : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag v-if="isHomeworkGraded(row)" type="success">已批改</el-tag>
            <el-tag v-else-if="row.submitTime" type="warning">已提交</el-tag>
            <el-tag v-else type="info">未提交</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!isHomeworkGraded(row)" size="small" type="primary" @click="handleGrade(row)">批改</el-button>
            <el-button v-else size="small" @click="handleGrade(row)">查看</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <AppEmpty />
        </template>
      </el-table>

      <el-pagination
        class="pagination"
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadSubmissions"
        @size-change="handleSizeChange"
      />
    </el-card>

    <!-- Grade Dialog -->
    <el-dialog v-model="gradeDialogVisible" title="批改作业" width="600px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="学生">学生{{ currentSubmission.studentId }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">
          {{ currentSubmission.submitTime ? new Date(currentSubmission.submitTime).toLocaleString() : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="提交内容">{{ currentSubmission.submitContent || '未填写文本答案' }}</el-descriptions-item>
        <el-descriptions-item label="附件" v-if="currentSubmission.attachmentUrl">
          <el-link :href="resolveResourceUrl(currentSubmission.attachmentUrl)" target="_blank" type="primary">查看附件</el-link>
        </el-descriptions-item>
      </el-descriptions>

      <el-form :model="gradeForm" :rules="gradeRules" ref="gradeFormRef" class="grade-form" label-width="100px">
        <el-form-item label="得分" prop="score">
          <el-input-number v-model="gradeForm.score" :min="0" :max="homework.fullScore || 100" />
          <span class="score-split">/ {{ homework.fullScore || 100 }}</span>
        </el-form-item>
        <el-form-item label="批改意见">
          <el-input v-model="gradeForm.feedback" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="gradeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmitGrade">提交批改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, reactive, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import AppEmpty from '@/components/AppEmpty.vue'
import { currentUserId, formatLocalDateTime, getRecords, getTotal } from '@/utils/apiHelpers'
import { resolveResourceUrl } from '@/utils/backendUrl'
import request from '@/utils/request'

const route = useRoute()
const userStore = useUserStore()
const homeworkId = computed(() => Number(route.params.id))

const loading = ref(false)
const submitLoading = ref(false)
const homework = ref({})
const submissions = ref([])
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const gradeDialogVisible = ref(false)
const currentSubmission = ref({})
const gradeFormRef = ref(null)
const gradeForm = reactive({
  score: 0,
  feedback: ''
})
const gradeRules = {
  score: [{ required: true, message: '请输入得分', trigger: 'blur' }]
}

const isHomeworkGraded = (row) => {
  return row.gradeStatus === 1 || row.gradeStatus === 2 || row.score != null
}

const loadHomework = async () => {
  try {
    const res = await request.get(`/homeworks/${homeworkId.value}`)
    if (res.code === '200') {
      homework.value = res.data
    }
  } catch (e) {
    // 全局已提示
  }
}

const loadSubmissions = async () => {
  loading.value = true
  try {
    const res = await request.get('/homework-submits', {
      params: {
        homeworkId: homeworkId.value,
        pageNum: pagination.pageNum,
        pageSize: pagination.pageSize
      }
    })
    if (res.code === '200') {
      submissions.value = getRecords(res.data)
      pagination.total = getTotal(res.data)
    }
  } catch (e) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const handleSizeChange = () => {
  pagination.pageNum = 1
  loadSubmissions()
}

const handleGrade = (row) => {
  currentSubmission.value = row
  gradeForm.score = row.score || 0
  gradeForm.feedback = row.feedback || ''
  gradeDialogVisible.value = true
}

const handleSubmitGrade = async () => {
  await gradeFormRef.value.validate()

  const data = {
    ...currentSubmission.value,
    score: gradeForm.score,
    feedback: gradeForm.feedback,
    gradeStatus: 1,
    graderId: currentUserId(userStore),
    gradeTime: formatLocalDateTime(new Date())
  }

  submitLoading.value = true
  try {
    const res = await request.put(`/homework-submits/${currentSubmission.value.id}`, data)
    if (res.code === '200') {
      ElMessage.success('批改成功')
      gradeDialogVisible.value = false
      loadSubmissions()
    }
  } catch (e) {
    // 全局已提示
  } finally {
    submitLoading.value = false
  }
}

const loadPage = () => {
  homework.value = {}
  submissions.value = []
  pagination.pageNum = 1
  pagination.total = 0
  loadHomework()
  loadSubmissions()
}

watch(
  () => route.params.id,
  loadPage,
  { immediate: true }
)
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.grade-form {
  margin-top: 20px;
}

.score-split {
  margin-left: 10px;
}

.pagination {
  margin-top: 20px;
  justify-content: center;
}
</style>
