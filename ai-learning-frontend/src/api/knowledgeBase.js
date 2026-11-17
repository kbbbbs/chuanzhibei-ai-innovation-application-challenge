// Existing teaching imports use the project's native knowledge service.
import * as rag from './rag'
import { getCourseGraph } from './courseGraph'
export const listKnowledgeBases = rag.listKnowledgeBases
export const createKnowledgeBase = rag.createKnowledgeBase
export const updateKnowledgeBase = rag.updateKnowledgeBase
export const deleteKnowledgeBase = rag.deleteKnowledgeBase
export const listKnowledge = rag.listDocuments
export const getKnowledgeContent = (_kbId, documentId) => rag.getDocumentChunks(documentId)
export const downloadKnowledgeFile = rag.downloadDocument
export const deleteKnowledge = rag.deleteDocument
export const uploadToKB = rag.uploadDocument
export const createSession = rag.createSession
export const listSessions = rag.listSessions
export const deleteSession = rag.deleteSession
export const searchKB = rag.search
export const getKnowledgeGraph = async kbId => {
  const base = rag.unwrapRag(await rag.getKnowledgeBase(kbId))
  const courseId = Number(base.courseId || base.course_id)
  if (!courseId) return { code: '200', data: { enabled: false, nodes: [], relations: [], assignmentRequired: true } }
  const graph = await getCourseGraph(courseId)
  return { code: '200', data: { ...graph, enabled: Boolean(graph.nodes?.length) } }
}
export const getWikiPage = async () => { throw new Error('当前知识库尚未提供百科页面') }
