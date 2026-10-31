import { ref } from 'vue'

/**
 * 统一管理异步请求的 loading / error / data
 * @param {Function} asyncFn 异步函数
 * @param {Object} options { immediate, defaultValue }
 * @returns { loading, error, data, run }
 */
export function useAsync(asyncFn, options = {}) {
  const loading = ref(false)
  const error = ref(null)
  const data = ref(options.defaultValue ?? null)

  const run = async (...args) => {
    loading.value = true
    error.value = null
    try {
      const res = await asyncFn(...args)
      data.value = res
      return res
    } catch (e) {
      error.value = e
      throw e
    } finally {
      loading.value = false
    }
  }

  if (options.immediate) {
    run()
  }

  return { loading, error, data, run }
}
