<template>
  <div class="workspace-page">
    <PageHeader title="助手设置" />
    <div class="settings-toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索名称或编码"
        clearable
        @change="load"
      /><el-button @click="load">刷新</el-button
      ><el-button type="primary" @click="edit()">新增助手配置</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="agents" v-loading="loading">
      <el-table-column
        prop="name"
        label="名称"
        min-width="150"
      /><el-table-column
        prop="code"
        label="编码"
        min-width="150"
      /><el-table-column prop="model" label="模型" min-width="150" />
      <el-table-column label="状态" width="90"
        ><template #default="{ row }">{{
          Number(row.status) === 1 ? '启用' : '停用'
        }}</template></el-table-column
      >
      <el-table-column label="操作" width="170"
        ><template #default="{ row }"
          ><el-button text @click="edit(row)">编辑</el-button
          ><el-button text @click="toggle(row)">{{
            Number(row.status) === 1 ? '停用' : '启用'
          }}</el-button></template
        ></el-table-column
      >
    </el-table>
    <el-dialog
      v-model="visible"
      :title="form.id ? '编辑助手配置' : '新增助手配置'"
      width="min(700px, 95vw)"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="编码" required
          ><el-input v-model="form.code" maxlength="64"
        /></el-form-item>
        <el-form-item label="名称" required
          ><el-input v-model="form.name" maxlength="100"
        /></el-form-item>
        <el-form-item label="描述"
          ><el-input v-model="form.description" maxlength="255"
        /></el-form-item>
        <el-form-item label="系统提示词"
          ><el-input v-model="form.systemPrompt" type="textarea" :rows="5"
        /></el-form-item>
        <el-form-item label="模型"
          ><el-input
            v-model="form.model"
            placeholder="留空使用模型设置中的生成模型"
        /></el-form-item>
        <el-form-item label="温度"
          ><el-input-number
            v-model="form.temperature"
            :min="0"
            :max="2"
            :step="0.1"
        /></el-form-item>
        <el-form-item label="启用工具"
          ><el-input
            v-model="form.enabledTools"
            type="textarea"
            :rows="3"
            placeholder='["course_search"]'
        /></el-form-item>
        <el-form-item label="状态"
          ><el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
        /></el-form-item>
      </el-form>
      <el-alert
        v-if="saveError"
        :title="saveError"
        type="error"
        :closable="false"
      />
      <template #footer
        ><el-button @click="visible = false">取消</el-button
        ><el-button
          type="primary"
          :loading="saving"
          :disabled="!form.code.trim() || !form.name.trim()"
          @click="save"
          >保存</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import {
  getAgentDefinitions,
  addAgentDefinition,
  updateAgentDefinition,
  updateAgentDefinitionStatus,
} from '@/api/agent'
import { assistantError } from '@/composables/useAssistant'
import { unwrapAssistant } from '@/api/assistant'
const agents = ref([]),
  keyword = ref(''),
  loading = ref(false),
  error = ref(''),
  visible = ref(false),
  saving = ref(false),
  saveError = ref('')
const defaults = () => ({
  id: null,
  code: '',
  name: '',
  description: '',
  systemPrompt: '',
  model: '',
  temperature: 0.7,
  enabledTools: '[]',
  status: 1,
  knowledgeBaseIds: '[]',
})
const form = reactive(defaults())
async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = unwrapAssistant(
      await getAgentDefinitions({ keyword: keyword.value }),
    )
    agents.value = Array.isArray(data) ? data : data?.records || []
  } catch (e) {
    error.value = assistantError(e)
  } finally {
    loading.value = false
  }
}
function edit(row) {
  Object.assign(form, defaults(), row || {})
  saveError.value = ''
  visible.value = true
}
async function save() {
  saving.value = true
  saveError.value = ''
  try {
    const tools = JSON.parse(form.enabledTools || '[]')
    if (!Array.isArray(tools) || tools.some((tool) => typeof tool !== 'string'))
      throw new Error('启用工具须为工具名称数组')
    const payload = {
      ...form,
      code: form.code.trim(),
      name: form.name.trim(),
      enabledTools: JSON.stringify(tools),
    }
    if (form.id) await updateAgentDefinition(form.id, payload)
    else await addAgentDefinition(payload)
    visible.value = false
    await load()
  } catch (e) {
    saveError.value = assistantError(e)
  } finally {
    saving.value = false
  }
}
async function toggle(row) {
  try {
    await updateAgentDefinitionStatus(row.id, Number(row.status) === 1 ? 0 : 1)
    await load()
  } catch (e) {
    error.value = assistantError(e)
  }
}
onMounted(load)
</script>
<style scoped>
.settings-toolbar {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 18px;
}
.settings-toolbar .el-input {
  width: 300px;
}
</style>
