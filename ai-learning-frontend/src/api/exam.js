import request from '@/utils/request'

// Question APIs
export const getQuestions = (params) => {
  return request.get('/api/questions', { params })
}

export const getQuestionById = (id) => {
  return request.get(`/api/questions/${id}`)
}

export const createQuestion = (data) => {
  return request.post('/api/questions', data)
}

export const updateQuestion = (id, data) => {
  return request.put(`/api/questions/${id}`, data)
}

export const deleteQuestion = (id) => {
  return request.delete(`/api/questions/${id}`)
}

// Paper APIs
export const getPapers = (params) => {
  return request.get('/api/papers', { params })
}

export const getPaperById = (id) => {
  return request.get(`/api/papers/${id}`)
}

export const createPaper = (data) => {
  return request.post('/api/papers', data)
}

export const updatePaper = (id, data) => {
  return request.put(`/api/papers/${id}`, data)
}

export const deletePaper = (id) => {
  return request.delete(`/api/papers/${id}`)
}

// Exam APIs
export const getExams = (params) => {
  return request.get('/api/exams', { params })
}

export const getExamById = (id) => {
  return request.get(`/api/exams/${id}`)
}

export const createExam = (data) => {
  return request.post('/api/exams', data)
}

export const updateExam = (id, data) => {
  return request.put(`/api/exams/${id}`, data)
}

export const publishExam = (id) => {
  return request.post(`/api/exams/${id}/publish`)
}

export const closeExam = (id) => {
  return request.post(`/api/exams/${id}/close`)
}

export const submitExamResults = (id) => {
  return request.post(`/api/exams/${id}/results/submit`)
}

export const getExamResults = (id) => {
  return request.get(`/api/exams/${id}/results`)
}

export const exportExamResults = (id, format = 'xlsx') => {
  return request.get(`/api/exams/${id}/results/export`, {
    params: { format },
    responseType: 'blob',
    silentError: true
  })
}

// Submission APIs
export const startExam = (examId, candidateId) => {
  return request.post(`/api/exams/${examId}/submissions/start`, null, {
    params: { candidateId }
  })
}

export const getSubmissions = (params) => {
  return request.get('/api/submissions', { params })
}

export const getSubmissionById = (id) => {
  return request.get(`/api/submissions/${id}`)
}

export const getStudentSubmission = (id) => {
  return request.get(`/api/submissions/${id}/student`)
}

export const saveAnswers = (submissionId, data) => {
  return request.post(`/api/submissions/${submissionId}/answers`, data)
}

export const submitExam = (submissionId) => {
  return request.post(`/api/submissions/${submissionId}/submit`)
}

export const reportScreenSwitch = (submissionId) => {
  return request.post(`/api/submissions/${submissionId}/screen-switch`)
}

export const gradeSubjective = (submissionId, data) => {
  return request.post(`/api/submissions/${submissionId}/grade-subjective`, data)
}

// PDF extract & batch
export const extractQuestions = (formData) => {
  return request.post('/api/questions/extract', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// Background generation tasks: the draft is stored on the server and survives closing the page.
const GEN_TASKS = '/api/questions/generation-tasks'
export const startQuestionGenerationTask = (data) => request.post(GEN_TASKS, data)
export const listQuestionGenerationTasks = (knowledgeBaseId) => request.get(GEN_TASKS, { params: { knowledgeBaseId }, silentError: true })
export const getQuestionGenerationTask = (id, config = {}) => request.get(`${GEN_TASKS}/${id}`, config)
export const updateQuestionGenerationDrafts = (id, questions, config = {}) => request.put(`${GEN_TASKS}/${id}/questions`, questions, config)
export const resumeQuestionGenerationTask = (id) => request.post(`${GEN_TASKS}/${id}/resume`)
export const saveQuestionGenerationTask = (id) => request.post(`${GEN_TASKS}/${id}/save`)
export const discardQuestionGenerationTask = (id) => request.post(`${GEN_TASKS}/${id}/discard`)

export const generateQuestionsFromKnowledgeBase = (data) => {
  return request.post('/api/questions/generate-from-kb', data, { timeout: 210000 })
}

export const batchCreateQuestions = (data) => {
  return request.post('/api/questions/batch', data)
}

export const uploadExamAnswerAttachment = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('bizType', 'exam-answer')
  return request.post('/files/upload', formData)
}
