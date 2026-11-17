import request from "@/utils/request";

const unwrap = async (promise) => {
  const result = await promise;
  if (result?.code && String(result.code) !== "200") {
    const error = new Error(result.msg || "知识图谱请求失败");
    error.code = String(result.code);
    throw error;
  }
  return result?.data ?? result;
};

const base = (courseId) => `/api/courses/${courseId}/knowledge-graph`;
export const getGraphScopeOptions = (courseId) =>
  unwrap(request.get(`${base(courseId)}/scope-options`, { silent: true }));
export const getCourseGraph = (courseId, config = {}) =>
  unwrap(request.get(base(courseId), { silent: true, ...config }));
export const getGraphNeighbors = (
  courseId,
  entityId,
  depth = 1,
  relationType = "",
  chapterId = undefined,
) =>
  unwrap(
    request.get(`${base(courseId)}/neighbors`, {
      silent: true,
      params: { entityId, depth, relationType, chapterId },
    }),
  );
export const getGraphPath = (courseId, from, to, chapterId = undefined) =>
  unwrap(
    request.get(`${base(courseId)}/paths`, {
      silent: true,
      params: { from, to, chapterId },
    }),
  );
export const generateCourseGraph = (courseId, requestId, scope = {}) =>
  unwrap(
    request.post(
      `${base(courseId)}/generate`,
      { requestId, ...scope },
      { silent: true },
    ),
  );
export const cancelCourseGraph = (courseId, jobId) =>
  unwrap(
    request.post(
      `${base(courseId)}/jobs/${jobId}/cancel`,
      {},
      { silent: true },
    ),
  );
