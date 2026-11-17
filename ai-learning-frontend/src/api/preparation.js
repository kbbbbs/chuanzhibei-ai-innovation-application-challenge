import request from '@/utils/request.js'

const base = '/api/preparations'

export const createPreparation = data => request.post(base, data)
export const listPreparations = (params = {}) => request.get(base, { params })
export const getPreparation = id => request.get(`${base}/${id}`)
export const updatePreparationConfig = (id, data) => request.put(`${base}/${id}/config`, data)

export const uploadPreparationSources = (id, files) => {
  const formData = new FormData()
  files.forEach(file => formData.append('files', file))
  return request.post(`${base}/${id}/sources`, formData)
}

export const generatePreparationOutline = id => request.post(`${base}/${id}/generate-outline`)
export const savePreparationOutline = (id, data, config = {}) => request.put(`${base}/${id}/outline`, data, config)
// mode: 'all' regenerates lessons the teacher has not edited; 'missing' fills only empty or failed ones.
export const generatePreparationLessons = (id, mode = 'all') => request.post(`${base}/${id}/generate-lessons`, null, { params: { mode } })
export const regeneratePreparationLesson = (id, lessonKey) => request.post(`${base}/${id}/lessons/${encodeURIComponent(lessonKey)}/regenerate`)
export const savePreparationLessons = (id, data, config = {}) => request.put(`${base}/${id}/lessons`, data, config)
export const generatePreparationQuestions = (id, mode = 'all') => request.post(`${base}/${id}/generate-questions`, null, { params: { mode } })
export const regeneratePreparationQuestions = (id, chapterKey) => request.post(`${base}/${id}/questions/${encodeURIComponent(chapterKey)}/regenerate`)
// One background run: outline, lessons and (if chosen) questions. Without restart it continues what is missing.
export const generatePreparationAll = (id, restart = false) => request.post(`${base}/${id}/generate-all`, null, { params: { restart } })
export const markPreparationReady = id => request.post(`${base}/${id}/ready`)
export const savePreparationQuestions = (id, data, config = {}) => request.put(`${base}/${id}/questions`, data, config)
export const publishPreparation = id => request.post(`${base}/${id}/publish`)

export const previewPreparationApplication = id => request.get(`${base}/${id}/apply-preview`)
export const applyPreparationToCourse = (id, data) => request.post(`${base}/${id}/apply`, data)
export const rollbackPreparationApplication = (id, applicationId) => request.post(`${base}/${id}/rollback`, { applicationId })
export const bindPreparationCourse = (id, courseId) => request.post(`${base}/${id}/bind-course`, { courseId })
