import request from '@/utils/request'

export const getAdministrativeClasses = (params) => {
  return request.get('/administrative-classes', { params })
}

export const getAdministrativeClassById = (id) => {
  return request.get(`/administrative-classes/${id}`)
}

export const createAdministrativeClass = (data) => {
  return request.post('/administrative-classes', data)
}

export const updateAdministrativeClass = (id, data) => {
  return request.put(`/administrative-classes/${id}`, data)
}

export const deleteAdministrativeClass = (id) => {
  return request.delete(`/administrative-classes/${id}`)
}

export const getAdministrativeClassStudents = (classId) => {
  return request.get(`/administrative-classes/${classId}/students`)
}

export const addAdministrativeClassStudent = (classId, data) => {
  return request.post(`/administrative-classes/${classId}/students`, data)
}

export const updateAdministrativeClassStudent = (classId, id, data) => {
  return request.put(`/administrative-classes/${classId}/students/${id}`, data)
}

export const removeAdministrativeClassStudent = (classId, id) => {
  return request.delete(`/administrative-classes/${classId}/students/${id}`)
}
