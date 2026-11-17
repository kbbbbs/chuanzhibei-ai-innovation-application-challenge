<template>
  <div class="category-manage workspace-page workspace-page--narrow">
    <PageHeader eyebrow="教学运营" title="分类管理" description="维护课程分类。分类被课程引用时，删除结果以服务端返回为准。">
      <template #actions>
        <el-button type="primary" @click="handleCreate">新增分类</el-button>
      </template>
    </PageHeader>

    <section class="filter-panel" aria-label="筛选分类">
      <el-form :inline="true" :model="searchForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="searchForm.keyword" placeholder="请输入分类名称或描述" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <ErrorNotice
      v-if="loadFailed"
      title="分类列表未加载"
      message="请确认网络后重试。"
      retry-label="重新加载"
      :busy="loading"
      @retry="loadCategories"
    />

    <section v-else class="table-panel" aria-label="分类列表">
      <el-table :data="categories" stripe v-loading="loading">
        <template #empty><AppEmpty /></template>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="分类名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="220" />
        <el-table-column label="操作" :width="narrow ? ROW_SHEET_COLUMN_WIDTH : 200" fixed="right">
          <template #default="{ row }">
            <RowActions :label="row.name || `分类 ${row.id}`">
              <el-button size="small" type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button size="small" type="danger" :loading="loading" @click="handleDelete(row)">删除</el-button>
            </RowActions>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="filteredCategories.length"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
      />
    </section>

    <!-- Form Dialog -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑分类' : '新增分类'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useConfirm } from '@/composables/useConfirm'
import AppEmpty from '@/components/AppEmpty.vue'
import ErrorNotice from '@/components/ui/ErrorNotice.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import RowActions from '@/components/ui/RowActions.vue'
import { useGuardedLoad } from '@/composables/useGuardedLoad'
import { ROW_SHEET_COLUMN_WIDTH, useNarrowScreen } from '@/composables/useNarrowScreen'
import { paginateRecords, requireSuccess } from '@/utils/apiHelpers'

const { confirmDelete } = useConfirm()

const formRef = ref(null)
const narrow = useNarrowScreen()

const allCategories = ref([])
const { loading, loadFailed, guarded } = useGuardedLoad(() => {
  allCategories.value = []
})
const dialogVisible = ref(false)

const searchForm = reactive({
  keyword: ''
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10
})

const filteredCategories = computed(() => {
  const keyword = searchForm.keyword.trim().toLowerCase()
  if (!keyword) return allCategories.value
  return allCategories.value.filter(item => {
    return [item.name, item.description]
      .some(value => String(value || '').toLowerCase().includes(keyword))
  })
})

const categories = computed(() => paginateRecords(filteredCategories.value, pagination.pageNum, pagination.pageSize))

const form = reactive({
  id: null,
  name: '',
  description: '',
  sortOrder: 1
})

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

const loadCategories = () => guarded(async (isCurrent) => {
  const res = requireSuccess(await request.get('/categories'))
  if (!isCurrent()) return
  allCategories.value = Array.isArray(res.data) ? res.data : []
  normalizePage()
})

const normalizePage = () => {
  const maxPage = Math.max(1, Math.ceil(filteredCategories.value.length / pagination.pageSize))
  if (pagination.pageNum > maxPage) {
    pagination.pageNum = maxPage
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
}

const handleSizeChange = () => {
  pagination.pageNum = 1
}

const handleReset = () => {
  searchForm.keyword = ''
  pagination.pageNum = 1
}

const handleCreate = () => {
  Object.assign(form, {
    id: null,
    name: '',
    description: '',
    sortOrder: 1
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  Object.assign(form, row)
  dialogVisible.value = true
}

const handleSubmit = async () => {
  await formRef.value.validate()

  loading.value = true
  try {
    let res
    if (form.id) {
      res = await request.put(`/categories/${form.id}`, form)
    } else {
      res = await request.post('/categories', form)
    }

    if (res.code === '200') {
      ElMessage.success('保存成功')
      dialogVisible.value = false
      loadCategories()
    }
  } catch (err) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

const handleDelete = async (row) => {
  const confirmed = await confirmDelete('确认删除此分类？')
  if (!confirmed) return

  loading.value = true
  try {
    const res = await request.delete(`/categories/${row.id}`)
    if (res.code === '200') {
      ElMessage.success('删除成功')
      loadCategories()
    }
  } catch (err) {
    // 全局已提示
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadCategories()
})
</script>
