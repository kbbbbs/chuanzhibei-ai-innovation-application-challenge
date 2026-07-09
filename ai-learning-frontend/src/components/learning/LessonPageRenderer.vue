<template>
  <article class="lesson-page">
    <section class="page-hero">
      <div>
        <p class="eyebrow">互动课本</p>
        <h2>{{ pageTitle }}</h2>
        <p class="summary">{{ pageSummary }}</p>
      </div>
      <div class="hero-meta">
        <el-tag :type="progress >= 100 ? 'success' : 'warning'" size="large">{{ progress }}%</el-tag>
        <span>预计 {{ estimatedMinutes }} 分钟</span>
      </div>
    </section>

    <section class="page-shell">
      <aside v-if="navigation.length" class="page-nav">
        <h3>本节导航</h3>
        <a v-for="item in navigation" :key="item.id" :href="`#${item.id}`">{{ item.title }}</a>
      </aside>

      <div class="block-stack">
        <section
          v-for="(block, index) in blocks"
          :id="anchorId(block, index)"
          :key="blockKey(block, index)"
          class="page-block"
          :class="`block-${block.type || 'markdown'}`"
        >
          <template v-if="block.type === 'markdown' || !block.type">
            <h3 v-if="block.title">{{ block.title }}</h3>
            <div class="markdown-body" v-html="markdownHtml(block.content)"></div>
          </template>

          <template v-else-if="block.type === 'experiment'">
            <InteractiveLabPanel :lesson-id="lesson?.id" :experiments="[toExperiment(block, index)]" :challenges="[]" />
          </template>

          <template v-else-if="block.type === 'ide'">
            <CodeLabPanel :lesson-id="lesson?.id" :code-labs="[toCodeLab(block)]" />
          </template>

          <template v-else-if="block.type === 'checkpoint'">
            <div class="block-title-row">
              <h3>{{ block.title || '完成检查' }}</h3>
              <el-tag>{{ checkedCount(block, index) }}/{{ checkpointItems(block).length }}</el-tag>
            </div>
            <el-checkbox-group v-model="checked[blockKey(block, index)]" class="checkpoint-list">
              <el-checkbox v-for="item in checkpointItems(block)" :key="item" :value="item">{{ item }}</el-checkbox>
            </el-checkbox-group>
            <el-progress :percentage="checkpointPercent(block, index)" />
          </template>

          <template v-else-if="block.type === 'challenge'">
            <div class="challenge-card">
              <el-tag type="danger">闯关题</el-tag>
              <h3>{{ block.title || '挑战题' }}</h3>
              <p>{{ block.prompt }}</p>
              <el-button size="small" @click="toggleChallenge(block, index)">
                {{ isChallengeOpen(block, index) ? '收起答案' : '查看答案' }}
              </el-button>
              <p v-if="isChallengeOpen(block, index)" class="challenge-answer">{{ block.answer || '教师尚未填写参考答案' }}</p>
            </div>
          </template>

          <template v-else-if="block.type === 'resource'">
            <div class="resource-card">
              <div>
                <h3>{{ block.title || '课程资料' }}</h3>
                <p>{{ block.description || '打开教师提供的资料完成本节学习。' }}</p>
              </div>
              <el-button type="primary" tag="a" :href="resourceHref(block.url)" target="_blank" rel="noopener noreferrer">
                打开资料
              </el-button>
            </div>
          </template>

          <template v-else-if="block.type === 'html'">
            <h3 v-if="block.title">{{ block.title }}</h3>
            <iframe class="html-frame" :srcdoc="block.html || block.content || ''" sandbox="allow-scripts allow-forms" />
          </template>

          <template v-else>
            <h3>{{ block.title || '学习内容' }}</h3>
            <div v-if="block.content" class="markdown-body" v-html="markdownHtml(block.content)"></div>
            <el-alert v-else type="info" :closable="false" title="这个内容块暂时无法显示" />
          </template>
        </section>
      </div>
    </section>
  </article>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import CodeLabPanel from '@/components/learning/CodeLabPanel.vue'
import InteractiveLabPanel from '@/components/learning/InteractiveLabPanel.vue'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps({
  page: { type: Object, default: null },
  lesson: { type: Object, default: null },
  progress: { type: Number, default: 0 },
  resolveUrl: { type: Function, default: value => value },
})

const checked = reactive({})
const openChallenges = ref(new Set())

const blocks = computed(() => Array.isArray(props.page?.blocks) ? props.page.blocks : [])
const pageTitle = computed(() => props.page?.title || props.lesson?.title || '课时学习')
const pageSummary = computed(() => props.page?.summary || firstMarkdownLine.value || '按讲解、实验、代码和检查清单完成本节学习。')
const progress = computed(() => Number(props.progress || props.lesson?.progress || 0))
const firstMarkdownLine = computed(() => {
  const markdown = blocks.value.find(block => (block.type || 'markdown') === 'markdown')?.content || ''
  return String(markdown).replace(/^#+\s+/gm, '').split(/\n+/).map(line => line.trim()).find(Boolean) || ''
})
const estimatedMinutes = computed(() => {
  const fromPage = Number(props.page?.estimatedMinutes || 0)
  if (fromPage > 0) return fromPage
  const chars = blocks.value.reduce((sum, block) => sum + String(block.content || block.starterCode || block.prompt || '').length, 0)
  return Math.max(3, Math.ceil(chars / 450))
})
const navigation = computed(() => blocks.value
  .map((block, index) => ({ id: anchorId(block, index), title: block.title || blockTypeLabel(block.type) }))
  .filter(item => item.title))

function markdownHtml(content) {
  return renderMarkdown(content || '')
}

function toExperiment(block, index) {
  return {
    id: block.id || `inline-experiment-${index}`,
    lessonId: props.lesson?.id,
    title: block.title || '互动实验',
    description: block.description || '',
    kind: block.kind || 'cards',
    defaults: block.defaults || block.config || {},
  }
}

function toCodeLab(block) {
  return {
    lessonId: props.lesson?.id,
    language: block.language || 'javascript',
    title: block.title || '在线 IDE',
    description: block.description || '',
    starterCode: block.starterCode || '',
    expectedOutputHint: block.expectedOutputHint || block.hint || '',
  }
}

function checkpointItems(block) {
  return Array.isArray(block.items) ? block.items.filter(Boolean) : []
}

function checkedCount(block, index) {
  return (checked[blockKey(block, index)] || []).length
}

function checkpointPercent(block, index) {
  const total = checkpointItems(block).length
  return total ? Math.round(checkedCount(block, index) / total * 100) : 0
}

function toggleChallenge(block, index) {
  const key = blockKey(block, index)
  const next = new Set(openChallenges.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  openChallenges.value = next
}

function isChallengeOpen(block, index) {
  return openChallenges.value.has(blockKey(block, index))
}

function resourceHref(url) {
  return props.resolveUrl(url || '')
}

function blockKey(block, index) {
  return block.id || `${index}-${block.type || 'markdown'}-${block.title || ''}`
}

function anchorId(block, index) {
  return `block-${index}-${slug(block.title || block.type || 'part')}`
}

function slug(value) {
  return String(value || '').replace(/[^\w\u4e00-\u9fa5]+/g, '-').replace(/^-|-$/g, '') || 'part'
}

function blockTypeLabel(type) {
  const labels = {
    markdown: '课本正文',
    experiment: '互动实验',
    ide: '在线 IDE',
    checkpoint: '完成检查',
    challenge: '闯关题',
    resource: '课程资料',
    html: '互动页面',
  }
  return labels[type] || '学习内容'
}
</script>

<style scoped>
.lesson-page { display: grid; gap: 18px; }
.page-hero {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
  padding: 24px;
  border-radius: 24px;
  color: #172033;
  background:
    radial-gradient(circle at 12% 16%, rgba(37, 99, 235, .18), transparent 26%),
    linear-gradient(135deg, #eef6ff, #fff7ed);
  border: 1px solid #dbeafe;
}
.eyebrow { margin: 0 0 8px; color: #2563eb; font-size: 12px; letter-spacing: .14em; text-transform: uppercase; }
h2 { margin: 0; font-size: 28px; }
.summary { color: #475569; line-height: 1.8; margin-bottom: 0; }
.hero-meta { display: grid; justify-items: end; gap: 8px; color: #64748b; font-size: 12px; white-space: nowrap; }
.page-shell { display: grid; grid-template-columns: 190px 1fr; gap: 16px; align-items: flex-start; }
.page-nav { position: sticky; top: 86px; display: grid; gap: 8px; padding: 16px; border: 1px solid #dbeafe; border-radius: 18px; background: #f8fbff; }
.page-nav h3 { margin: 0 0 6px; color: #172033; }
.page-nav a { color: #2563eb; text-decoration: none; line-height: 1.5; font-size: 13px; }
.page-nav a:hover { text-decoration: underline; }
.block-stack { display: grid; gap: 16px; }
.page-block { border: 1px solid #e2e8f0; border-radius: 20px; padding: 18px; background: #fff; }
.page-block h3 { margin: 0 0 14px; color: #172033; }
.block-experiment,
.block-ide { padding: 0; border: none; background: transparent; }
.markdown-body { color: #334155; line-height: 1.9; }
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) { color: #172033; margin: 18px 0 10px; }
.markdown-body :deep(p) { margin: 0 0 12px; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { padding-left: 22px; }
.markdown-body :deep(pre) { padding: 14px; border-radius: 14px; background: #0f172a; color: #d1fae5; overflow: auto; }
.markdown-body :deep(code) { font-family: Consolas, Monaco, monospace; }
.markdown-body :deep(:not(pre) > code) { background: #f1f5f9; color: #1e3a8a; border-radius: 6px; padding: 2px 5px; }
.block-title-row,
.resource-card { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.checkpoint-list { display: grid; gap: 8px; margin-bottom: 12px; }
.checkpoint-list :deep(.el-checkbox) { height: auto; align-items: flex-start; white-space: normal; }
.challenge-card { display: grid; gap: 10px; }
.challenge-card p { margin: 0; line-height: 1.8; color: #475569; }
.challenge-answer { padding: 12px; border-radius: 14px; background: #fef3c7; color: #92400e !important; }
.resource-card { padding: 4px; }
.resource-card p { color: #64748b; line-height: 1.7; margin: 0; }
.html-frame { width: 100%; min-height: 420px; border: 1px solid #dbeafe; border-radius: 18px; background: white; }
@media (max-width: 900px) { .page-shell { grid-template-columns: 1fr; } .page-nav { position: static; } }
@media (max-width: 720px) { .page-hero, .block-title-row, .resource-card { flex-direction: column; align-items: flex-start; } .hero-meta { justify-items: start; } h2 { font-size: 22px; } }
</style>
