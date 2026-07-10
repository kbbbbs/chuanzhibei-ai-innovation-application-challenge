import request from '@/utils/request'

export const getOperationLogs = (params) => {
  return request.get('/operation-logs', { params })
}

export const getOperationLogById = (id) => {
  return request.get(`/operation-logs/${id}`)
}
