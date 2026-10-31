import request from '@/utils/request'

export const getHomeCourseRecommendations = () => request.get('/api/home-course-recommendations')
export const joinHomeCourseRecommendation = courseId => request.post(`/api/home-course-recommendations/${courseId}/join`)
export const getHomeCourseRecommendationsForAdmin = () => request.get('/api/home-course-recommendations/admin')
export const createHomeCourseRecommendation = data => request.post('/api/home-course-recommendations', data)
export const updateHomeCourseRecommendation = (id, data) => request.put(`/api/home-course-recommendations/${id}`, data)
export const deleteHomeCourseRecommendation = id => request.delete(`/api/home-course-recommendations/${id}`)
