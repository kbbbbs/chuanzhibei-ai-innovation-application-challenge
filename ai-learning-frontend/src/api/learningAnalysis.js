import request from '@/utils/request'

async function data(promise) {
  const result = await promise
  if (result.code !== '200') throw new Error(result.msg || '学情分析请求失败')
  return result.data
}

export const listReportTemplates = () => data(request.get('/api/learning-analysis/templates'))
export const listLearningReports = courseId => data(request.get('/api/learning-analysis/reports', { params: { courseId } }))
export const getLearningReport = id => data(request.get(`/api/learning-analysis/reports/${id}`))
export const createLearningReport = input => data(request.post('/api/learning-analysis/reports', input))
export function uploadReportTemplate(file) {
  const form = new FormData()
  form.append('file', file)
  return data(request.post('/api/learning-analysis/templates', form))
}

export async function downloadAnalysisFile(path, filename) {
  const blob = await request.get(`/api/learning-analysis/${path}`, { responseType: 'blob' })
  if (!(blob instanceof Blob)) throw new Error('下载未返回有效文件')
  if (blob.type.includes('json')) {
    const error = JSON.parse(await blob.text())
    throw new Error(error.msg || error.message || '文件下载失败')
  }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
