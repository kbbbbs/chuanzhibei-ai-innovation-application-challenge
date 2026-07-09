import request from '@/utils/request'
import { formatLocalDateTime } from '@/utils/apiHelpers'

// Homework APIs
export const getHomeworks = (params) => {
  return request.get('/homeworks', { params })
}

export const getHomeworkById = (id) => {
  return request.get(`/homeworks/${id}`)
}

export const getHomeworkSubmits = (homeworkId) => {
  return request.get(`/homeworks/${homeworkId}/submits`)
}

export const createHomework = (data) => {
  return request.post('/homeworks', data)
}

export const updateHomework = (id, data) => {
  return request.put(`/homeworks/${id}`, data)
}

export const deleteHomework = (id) => {
  return request.delete(`/homeworks/${id}`)
}

// Homework Submit APIs
export const getHomeworkSubmit = (params) => {
  return request.get('/homework-submits', { params })
}

export const getHomeworkSubmitById = (id) => {
  return request.get(`/homework-submits/${id}`)
}

export const submitHomework = (data) => {
  return request.post('/homework-submits', data)
}

export const uploadHomeworkAttachment = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', 'homework')
  return request.post('/files/upload', formData)
}

export const updateHomeworkSubmit = (id, data) => {
  return request.put(`/homework-submits/${id}`, data)
}

export const gradeHomework = (id, data) => {
  return request.put(`/homework-submits/${id}`, {
    ...data,
    gradeStatus: 1,
    gradeTime: formatLocalDateTime(new Date())
  })
}
