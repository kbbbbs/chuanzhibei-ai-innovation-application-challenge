import request from '@/utils/request'

export const submitVideoUrl = (lessonId, url, config = {}) => {
  return request.post('/video-resources/url', { lessonId, url }, config)
}

export const getVideoResource = (id) => {
  return request.get(`/video-resources/${id}`)
}

export const getLessonPlayback = (lessonId) => {
  return request.get(`/lessons/${lessonId}/playback`)
}

export const retryVideoDownload = (id) => {
  return request.post(`/video-resources/${id}/retry`)
}

export const uploadLessonResource = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', 'lesson-resource')
  return request.post('/files/upload', formData)
}
