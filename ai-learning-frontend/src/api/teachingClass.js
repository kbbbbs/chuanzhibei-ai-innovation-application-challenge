import request from '@/utils/request'

// Teaching Class APIs
export const getTeachingClasses = (params) => {
  return request.get('/teaching-classes', { params })
}

export const getTeachingClassById = (id) => {
  return request.get(`/teaching-classes/${id}`)
}

export const getClassStudents = (classId) => {
  return request.get(`/teaching-classes/${classId}/students`)
}

export const createTeachingClass = (data) => {
  return request.post('/teaching-classes', data)
}

export const updateTeachingClass = (id, data) => {
  return request.put(`/teaching-classes/${id}`, data)
}

export const deleteTeachingClass = (id) => {
  return request.delete(`/teaching-classes/${id}`)
}

// Class Student APIs
export const getClassStudentList = (params) => {
  return request.get('/class-students', { params })
}

export const addClassStudent = (data) => {
  return request.post('/class-students', data)
}

export const removeClassStudent = (id) => {
  return request.delete(`/class-students/${id}`)
}
