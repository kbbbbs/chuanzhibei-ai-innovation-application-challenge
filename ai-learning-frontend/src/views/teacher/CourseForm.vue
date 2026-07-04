<template>
  <div class="course-form">
    <el-card v-loading="loading">
      <template #header>
        <span>{{ isEdit ? '编辑课程' : '新建课程' }}</span>
      </template>

      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="课程名称" prop="title">
          <el-input v-model="form.title" placeholder="请输入课程名称" />
        </el-form-item>

        <el-form-item label="课程分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择分类">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="难度" prop="difficulty">
          <el-select v-model="form.difficulty" placeholder="请选择难度">
            <el-option label="入门" value="beginner" />
            <el-option label="基础" value="intermediate" />
            <el-option label="进阶" value="advanced" />
          </el-select>
        </el-form-item>

        <el-form-item label="课程简介" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" placeholder="请输入课程简介" />
        </el-form-item>

        <el-form-item label="课程封面">
          <el-input v-model="form.coverUrl" placeholder="请输入封面URL" />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="2">草稿</el-radio>
            <el-radio :label="1">发布</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">提交</el-button>
          <el-button @click="handleCancel">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getCourseById, addCourse, updateCourse, getCategories } from '@/utils/course'
import { currentUserId } from '@/utils/apiHelpers'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const formRef = ref(null)

const isEdit = ref(false)
const editingId = computed(() => {
  const id = Number(route.query.id)
  return Number.isFinite(id) && id > 0 ? id : null
})
const loading = ref(false)
const submitLoading = ref(false)
const categories = ref([])

const defaultForm = () => ({
  title: '',
  categoryId: null,
  teacherId: currentUserId(userStore),
  difficulty: 'beginner',
  description: '',
  coverUrl: '',
  status: 1
})

const form = reactive({
  ...defaultForm()
})

const rules = {
  title: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
}

const loadCategories = async () => {
  try {
    const res = await getCategories()
    if (res.code === '200') {
      categories.value = res.data
    }
  } catch (e) {
    // 全局已提示
  }
}

const loadCourse = async (id) => {
  try {
    const res = await getCourseById(id)
    if (res.code === '200') {
      Object.assign(form, res.data)
    }
  } catch (e) {
    // 全局已提示
  }
}

const handleSubmit = async () => {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  const data = { ...form }
  let res

  submitLoading.value = true
  try {
    if (isEdit.value && editingId.value) {
      res = await updateCourse(editingId.value, data)
    } else {
      res = await addCourse(data)
    }

    if (res.code === '200') {
      ElMessage.success(isEdit.value ? '修改成功' : '创建成功')
      router.push('/courses')
    }
  } catch (e) {
    // 全局已提示
  } finally {
    submitLoading.value = false
  }
}

const handleCancel = () => {
  router.back()
}

onMounted(() => {
  loadCategories()
})

watch(
  editingId,
  async (id) => {
    formRef.value?.clearValidate?.()
    Object.assign(form, defaultForm())
    isEdit.value = !!id

    if (!id) return

    loading.value = true
    try {
      await loadCourse(id)
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)
</script>
