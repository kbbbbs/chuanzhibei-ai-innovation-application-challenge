// Restored event labels and validation from a1b688c. Transport belongs to /api/assistant.
export const kernelStateLabels = {
  REQUEST_ACCEPTED: '已接收请求', CONTEXT_VALIDATING: '校验身份与资料范围',
  MODEL_RUNNING: '调用模型', MODEL_REQUEST: '模型请求', OUTPUT_REPAIR: '修复回答格式',
  TOOL_VALIDATING: '校验工具参数', TOOL_RUNNING: '执行工具', TOOL_SUCCEEDED: '工具执行成功', TOOL_FAILED: '工具执行失败',
  RESULT_VALIDATING: '校验结果与权限', RESPONSE_SAVING: '保存对话结果',
  LIMIT_REACHED: '达到执行限制', WAITING_INPUT: '等待补充信息', COMPLETED: '执行完成',
  FAILED: '执行失败', TIMED_OUT: '执行超时', CANCELLED: '执行已结束',
  JOB_SUBMITTED: '作业已受理', WAITING_JOB: '等待作业结果', MODEL_REPLY_UNAVAILABLE: '模型后续回复未完成，保留已受理作业',
}

export function appendKernelEvent(events, value) {
  if (!value || !Number.isSafeInteger(value.sequence) || value.sequence !== events.length + 1
      || !Object.hasOwn(kernelStateLabels, value.state) || !Number.isFinite(value.elapsedMillis) || value.elapsedMillis < 0) {
    throw new Error('运行状态顺序或格式错误')
  }
  const result = value.result && {
    itemCount: Number.isSafeInteger(value.result.itemCount) && value.result.itemCount >= 0 ? value.result.itemCount : null,
    entityIds: Array.isArray(value.result.entityIds) ? value.result.entityIds.filter(id => typeof id === 'string' && /^[A-Za-z0-9_-]{1,64}$/.test(id)).slice(0, 10) : [],
  }
  events.push({ sequence: value.sequence, state: value.state,
    toolName: typeof value.toolName === 'string' ? value.toolName : null, elapsedMillis: value.elapsedMillis,
    ...(result ? { result } : {}) })
}

