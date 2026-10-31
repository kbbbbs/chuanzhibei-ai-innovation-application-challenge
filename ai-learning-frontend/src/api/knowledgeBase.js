// Existing teaching imports use the project's native knowledge service.
import * as rag from './rag'
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
// A disabled view is explicit; no unrelated entity graph is presented as retrieved evidence.
export const getKnowledgeGraph = async () => ({ code: '200', data: { enabled: false, nodes: [], relations: [], unsupported: true } })
export const getWikiPage = async () => { throw new Error('当前知识库尚未提供百科页面') }
