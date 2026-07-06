import request from '@/utils/request'

// 岗位管理
export const getWorkStudies = (params) => {
  return request.get('/work-studies', { params })
}

export const getWorkStudyById = (id) => {
  return request.get(`/work-studies/${id}`)
}

export const createWorkStudy = (data) => {
  return request.post('/work-studies', data)
}

export const updateWorkStudy = (id, data) => {
  return request.put(`/work-studies/${id}`, data)
}

export const deleteWorkStudy = (id) => {
  return request.delete(`/work-studies/${id}`)
}

// 学生劳务费管理
export const getWorkStudyStudents = (params) => {
  return request.get('/work-studies/students', { params })
}

export const getWorkStudyStudentById = (id) => {
  return request.get(`/work-studies/students/${id}`)
}

export const createWorkStudyStudent = (data) => {
  return request.post('/work-studies/students', data)
}

export const updateWorkStudyStudent = (id, data) => {
  return request.put(`/work-studies/students/${id}`, data)
}

export const deleteWorkStudyStudent = (id) => {
  return request.delete(`/work-studies/students/${id}`)
}

export const importWorkStudyStudents = (workStudyId, file) => {
  const formData = new FormData()
  formData.append('workStudyId', workStudyId)
  formData.append('file', file)
  return request.post('/work-studies/students/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const exportWorkStudyStudents = (params) => {
  return request.get('/work-studies/students/export', { params, responseType: 'blob' })
}

// 学生申请岗位
export const applyForPosition = (data) => {
  return request.post('/work-studies/apply', data)
}

// 教师审核申请
export const auditApplication = (data) => {
  return request.put('/work-studies/audit', data)
}

// 学生查看我的申请
export const getMyApplications = (params) => {
  return request.get('/work-studies/my-applications', { params })
}

// 教师查看待审核申请
export const getPendingApplications = (params) => {
  return request.get('/work-studies/pending-applications', { params })
}
