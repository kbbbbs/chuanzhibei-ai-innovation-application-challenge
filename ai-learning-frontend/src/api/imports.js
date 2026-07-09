import request from '@/utils/request'

export const previewImport = (type, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/api/imports/${type}/preview`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    silentError: true
  })
}

export const confirmImport = (type, items) => {
  return request.post(`/api/imports/${type}/confirm`, { items }, { silentError: true })
}

export const downloadImportTemplate = (type) => {
  return request.get(`/api/imports/${type}/template`, {
    responseType: 'blob',
    silentError: true
  })
}
