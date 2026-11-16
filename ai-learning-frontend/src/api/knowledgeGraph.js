import request from '@/utils/request'

const base = '/api/rag/graphs'
async function checked(promise) {
  try {
    const result = await promise
    if (result?.code && String(result.code) !== '200')
      throw new Error(result.msg || '图谱请求失败')
    return result?.data ?? result
  } catch (error) {
    const failure = new Error(
      error.response?.data?.msg ||
        error.response?.data?.message ||
        error.message ||
        '无法连接图谱服务',
    )
    failure.status = error.response?.status || error.status
    throw failure
  }
}
const options = { silentError: true }
export const listGraphs = (courseId) =>
  checked(request.get(base, { ...options, params: { courseId } }))
export const createGraph = (data) => checked(request.post(base, data, options))
export const getGraph = (id, draft = false) =>
  checked(
    request.get(`${base}/${encodeURIComponent(id)}`, {
      ...options,
      params: { draft },
    }),
  )
export const searchGraphEntities = (id, keyword) =>
  checked(
    request.get(`${base}/${encodeURIComponent(id)}/entities`, {
      ...options,
      params: { keyword },
    }),
  )
export const getGraphSources = (id) =>
  checked(request.get(`${base}/${encodeURIComponent(id)}/sources`, options))
export const saveGraphDraft = (id, data) =>
  checked(
    request.patch(`${base}/${encodeURIComponent(id)}/draft`, data, options),
  )
export const publishGraph = (id, revision) =>
  checked(
    request.post(
      `${base}/${encodeURIComponent(id)}/publish`,
      { revision },
      options,
    ),
  )
export const getGraphJob = (id, jobId) =>
  checked(
    request.get(
      `${base}/${encodeURIComponent(id)}/jobs/${encodeURIComponent(jobId)}`,
      options,
    ),
  )
export const retryGraphJob = (id, jobId) =>
  checked(
    request.post(
      `${base}/${encodeURIComponent(id)}/jobs/${encodeURIComponent(jobId)}/retry`,
      {},
      options,
    ),
  )
export const getGraphNeighbors = (id, entityId, relationType = '') =>
  checked(
    request.get(
      `${base}/${encodeURIComponent(id)}/entities/${encodeURIComponent(entityId)}/neighbors`,
      { ...options, params: { depth: 1, relationType } },
    ),
  )
export const getGraphPaths = (id, from, to, learningOnly) =>
  checked(
    request.get(`${base}/${encodeURIComponent(id)}/paths`, {
      ...options,
      params: { from, to, learningOnly },
    }),
  )
export const getGraphEvidence = (id, evidenceId) =>
  checked(
    request.get(
      `${base}/${encodeURIComponent(id)}/evidence/${encodeURIComponent(evidenceId)}`,
      options,
    ),
  )
export const extractGraphDraft = (id, revision) =>
  checked(
    request.post(
      `${base}/${encodeURIComponent(id)}/extract`,
      { revision },
      { ...options, timeout: 120000 },
    ),
  )
