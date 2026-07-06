import request from '@/utils/request'

export const getLessonNote = (lessonId) => {
  return request.get(`/lessons/${lessonId}/note`)
}

export const saveLessonNote = (lessonId, data) => {
  return request.put(`/lessons/${lessonId}/note`, data)
}

export const downloadLessonNote = (lessonId) => {
  return request.get(`/lessons/${lessonId}/note/download`, {
    responseType: 'blob'
  })
}
