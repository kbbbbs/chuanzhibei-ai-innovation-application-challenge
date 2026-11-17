<template>
  <div v-if="!narrow" class="row-actions"><slot /></div>
  <template v-else>
    <el-button size="small" aria-haspopup="dialog" :aria-label="`${label} 的操作`" @click="openSheet">操作</el-button>
    <el-drawer
      v-if="mounted"
      v-model="visible"
      class="row-action-sheet"
      :title="label"
      direction="btt"
      size="auto"
      append-to-body
    >
      <div class="row-action-list" @click="closeAfterAction"><slot /></div>
    </el-drawer>
  </template>
</template>

<script setup>
import { ref } from 'vue'
import { useNarrowScreen } from '@/composables/useNarrowScreen'

// Wide action columns would cover the row they act on, so on phones they collapse to one
// button whose sheet is titled with that row. The label must identify the row.
defineProps({
  label: { type: String, required: true },
})

const narrow = useNarrowScreen()
const mounted = ref(false)
const visible = ref(false)

function openSheet() {
  mounted.value = true
  visible.value = true
}

function closeAfterAction(event) {
  if (event.target.closest('button, a')) visible.value = false
}
</script>
