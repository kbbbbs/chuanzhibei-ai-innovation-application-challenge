import request from '@/utils/request'

export const getLessonComments = (lessonId, params = {}) => {
  return request.get(`/lessons/${lessonId}/comments`, { params })
}

export const createLessonComment = (lessonId, data) => {
  return request.post(`/lessons/${lessonId}/comments`, data)
}

export const deleteLessonComment = (commentId) => {
  return request.delete(`/lesson-comments/${commentId}`)
}
