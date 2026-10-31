<template>
  <video ref="videoRef" class="plyr-video-player" playsinline controls></video>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import Plyr from 'plyr'
import 'plyr/dist/plyr.css'

const props = defineProps({
  src: {
    type: String,
    required: true
  },
  options: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['ready', 'timeupdate', 'ended'])

const videoRef = ref(null)
let player = null

const sourceType = computed(() => {
  const lowerSrc = props.src.toLowerCase().split('?')[0]
  if (lowerSrc.endsWith('.webm')) return 'video/webm'
  if (lowerSrc.endsWith('.mov')) return 'video/quicktime'
  return 'video/mp4'
})

const syncVideoElementSource = () => {
  if (!videoRef.value) return
  videoRef.value.src = props.src
  videoRef.value.load()
}

const buildPlayerSource = () => ({
  type: 'video',
  sources: [
    {
      src: props.src,
      type: sourceType.value
    }
  ]
})

const createPlayer = async () => {
  destroyPlayer()
  await nextTick()
  if (!videoRef.value || !props.src) return

  syncVideoElementSource()
  player = new Plyr(videoRef.value, {
    controls: [
      'play-large',
      'play',
      'progress',
      'current-time',
      'mute',
      'volume',
      'settings',
      'fullscreen'
    ],
    ...props.options
  })
  player.on('ready', () => emit('ready', player))
  player.on('timeupdate', () => emit('timeupdate', Math.floor(player.currentTime || 0)))
  player.on('ended', () => emit('ended'))
}

const destroyPlayer = () => {
  if (!player) return
  player.destroy()
  player = null
}

onMounted(createPlayer)
onBeforeUnmount(destroyPlayer)

watch(
  () => props.src,
  async () => {
    if (!props.src) {
      destroyPlayer()
      return
    }
    await nextTick()
    if (!player) {
      await createPlayer()
      return
    }
    player.source = buildPlayerSource()
    syncVideoElementSource()
  }
)
</script>

<style scoped>
.plyr-video-player {
  width: 100%;
}
</style>
