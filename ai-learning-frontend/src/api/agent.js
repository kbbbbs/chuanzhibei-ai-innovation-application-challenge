import request from '@/utils/request'

export const listAgentDefinitions = async (params) => {
  const res = await request.get('/api/agent/definitions', { params, silent: true }).catch(() => null)
  if (res && res.code === '200' && res.data) {
    return res
  }
  return res || { code: '200', data: [] }
}
