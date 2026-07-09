import request from '@/utils/request'

// Notice APIs
export const getNotices = (params) => {
  return request.get('/notices', { params })
}

export const getNoticeById = (id) => {
  return request.get(`/notices/${id}`)
}

export const createNotice = (data) => {
  return request.post('/notices', data)
}

export const updateNotice = (id, data) => {
  return request.put(`/notices/${id}`, data)
}

export const deleteNotice = (id) => {
  return request.delete(`/notices/${id}`)
}
