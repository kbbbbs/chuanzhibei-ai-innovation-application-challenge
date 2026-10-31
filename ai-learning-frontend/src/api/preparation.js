import request from '@/utils/request.js'

const base = '/api/preparations'

export const createPreparation = data => request.post(base, data)
export const listPreparations = () => request.get(base)
export const getPreparation = id => request.get(`${base}/${id}`)
export const updatePreparationConfig = (id, data) => request.put(`${base}/${id}/config`, data)

export const uploadPreparationSources = (id, files) => {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  return request.post(`${base}/${id}/sources`, formData)
}

export const generatePreparationOutline = id => request.post(`${base}/${id}/generate-outline`)
export const savePreparationOutline = (id, data) => request.put(`${base}/${id}/outline`, data)
export const generatePreparationLessons = id => request.post(`${base}/${id}/generate-lessons`)
export const savePreparationLessons = (id, data) => request.put(`${base}/${id}/lessons`, data)
export const generatePreparationQuestions = id => request.post(`${base}/${id}/generate-questions`)
export const savePreparationQuestions = (id, data) => request.put(`${base}/${id}/questions`, data)
export const publishPreparation = id => request.post(`${base}/${id}/publish`)
