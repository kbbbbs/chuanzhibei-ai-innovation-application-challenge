import request from '@/utils/request'

export const getMyRecommendations = (params) => request.get('/api/recommendations/my', { params })
