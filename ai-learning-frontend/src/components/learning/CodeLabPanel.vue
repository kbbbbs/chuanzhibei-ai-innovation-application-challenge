<template>
  <el-card class="code-lab-card">
    <template #header>
      <div class="card-header">
        <span>在线 IDE</span>
        <el-tag type="success">{{ labs.length }} 个实验</el-tag>
      </div>
    </template>

    <el-alert
      v-if="!labs.length"
      title="当前课程还没有配置代码实验"
      description="教师可以在课程课时中加入代码实验；系统会根据课程类型提供 Java、Python 或 JavaScript 沙盒。"
      type="warning"
      :closable="false"
    />

    <template v-else>
      <el-select v-model="selectedKey" class="language-select" @change="switchLab">
        <el-option
          v-for="lab in labs"
          :key="labKey(lab)"
          :label="lab.title || lab.language"
          :value="labKey(lab)"
        />
      </el-select>
      <p class="lab-description">{{ currentLab?.description || currentLab?.expectedOutputHint }}</p>
      <el-input v-model="code" type="textarea" :rows="14" spellcheck="false" class="code-box" />
      <div class="ide-actions">
        <el-button type="primary" :loading="running" @click="runCode">运行代码</el-button>
        <el-button @click="resetCode">恢复示例</el-button>
      </div>
      <p class="hint">{{ currentLab?.expectedOutputHint }}</p>
      <pre class="output" :class="{ error: hasError }">{{ output || '点击“运行代码”后，这里会显示标准输出、编译错误或运行错误。' }}</pre>
    </template>
  </el-card>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  codeLabs: { type: Array, default: () => [] },
  lessonId: { type: [Number, String], default: null },
})

const running = ref(false)
const selectedKey = ref('')
const code = ref('')
const output = ref('')
const hasError = ref(false)

const fallbackLab = {
  language: 'javascript',
  title: 'JavaScript：即时反馈示例',
  description: '当前课程未配置专属代码实验，先用这个示例确认 IDE 可运行。',
  starterCode: "console.log('Amazing Teaching IDE ready')",
  expectedOutputHint: '应该输出 Amazing Teaching IDE ready。',
}

const labs = computed(() => {
  const source = props.codeLabs || []
  const lessonId = Number(props.lessonId)
  const matched = source.filter(lab => !lab.lessonId || Number(lab.lessonId) === lessonId)
  const result = matched.length ? matched : source
  return result.length ? result : [fallbackLab]
})
const currentLab = computed(() => labs.value.find(lab => labKey(lab) === selectedKey.value) || labs.value[0])

const labKey = lab => `${lab.lessonId || 'course'}:${lab.language}:${lab.title}`

const setCurrentCode = () => {
  const lab = currentLab.value
  selectedKey.value = lab ? labKey(lab) : ''
  code.value = lab?.starterCode || ''
  output.value = ''
  hasError.value = false
}

const switchLab = () => {
  code.value = currentLab.value?.starterCode || ''
  output.value = ''
  hasError.value = false
}

const resetCode = () => {
  code.value = currentLab.value?.starterCode || ''
  output.value = ''
  hasError.value = false
}

const runCode = async () => {
  if (!currentLab.value) return
  if (!code.value.trim()) {
    ElMessage.warning('请先输入代码')
    return
  }
  running.value = true
  output.value = ''
  hasError.value = false
  try {
    const res = await request.post('/code-lab/run', { language: currentLab.value.language, code: code.value })
    if (res.code === '200') {
      const data = res.data || {}
      hasError.value = Boolean(data.stderr) || Number(data.exitCode) !== 0 || data.timedOut
      output.value = [data.stdout, data.stderr].filter(Boolean).join('\n') || `运行结束，退出码 ${data.exitCode}`
    } else {
      hasError.value = true
      output.value = res.msg || '代码运行失败'
    }
  } catch (e) {
    hasError.value = true
    output.value = '代码运行服务暂时不可用：请稍后重试，或联系教师检查运行环境。'
  } finally {
    running.value = false
  }
}

watch(labs, setCurrentCode, { immediate: true })
watch(() => props.lessonId, setCurrentCode)
</script>

<style scoped>
.card-header, .ide-actions { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.language-select { width: 100%; margin-bottom: 10px; }
.lab-description, .hint { color: #64748b; line-height: 1.7; }
.code-box :deep(textarea) { font-family: Consolas, Monaco, monospace; }
.ide-actions { justify-content: flex-start; margin-top: 12px; }
.output { min-height: 120px; margin: 12px 0 0; padding: 14px; border-radius: 12px; background: #0f172a; color: #d1fae5; white-space: pre-wrap; overflow: auto; }
.output.error { color: #fecaca; }
</style>
