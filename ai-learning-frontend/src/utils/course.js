import request from './request.js'

export const getCategories = () => {
  return request.get('/categories')
}

export const addCategory = data => {
  return request.post('/categories', data)
}

export const updateCategory = (id, data) => {
  return request.put('/categories/' + id, data)
}

export const deleteCategory = id => {
  return request.delete('/categories/' + id)
}

export const getCourses = params => {
  return request.get('/courses', { params })
}

export const getCourseById = id => {
  return request.get('/courses/' + id)
}

export const addCourse = data => {
  return request.post('/courses', data)
}

export const updateCourse = (id, data) => {
  return request.put('/courses/' + id, data)
}

export const deleteCourse = id => {
  return request.delete('/courses/' + id)
}

export const addCourseView = id => {
  return request.put('/courses/' + id + '/view')
}
