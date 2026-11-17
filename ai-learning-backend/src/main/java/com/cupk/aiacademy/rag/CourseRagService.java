package com.cupk.aiacademy.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Retrieval over explicit course grants, pinned to their authorized document versions. */
@Service
public class CourseRagService {
    public record DocumentScope(String documentId, int documentVersion, String indexVersion) {}
    private final JdbcTemplate db;
    private final RagAuthorizationService authorization;
    private final RagRetrievalService retrieval;

    public CourseRagService(JdbcTemplate db, RagAuthorizationService authorization, RagRetrievalService retrieval) {
        this.db = db;
        this.authorization = authorization;
        this.retrieval = retrieval;
    }

    public List<DocumentScope> scope(Integer courseId, Integer lessonId, int userId) {
        var actor = authorization.actorForUser(userId);
        authorization.requireCourseRead(actor, courseId);
        if (lessonId != null) {
            Integer count = db.queryForObject("SELECT COUNT(*) FROM t_lesson l JOIN t_chapter c ON c.id=l.chapter_id "
                    + "WHERE l.id=? AND c.course_id=? AND l.status=1 AND c.status=1",Integer.class,lessonId,courseId);
            if (count == null || count != 1) throw new RagAccessException("403","课时不属于当前课程或尚未发布");
        }
        return db.queryForList("SELECT g.document_id,g.document_version,v.active_index_version "
                + "FROM native_rag_course_grant g JOIN native_rag_document_version v "
                + "ON v.document_id=g.document_id AND v.document_version=g.document_version "
                + "JOIN native_rag_index_version i ON i.id=v.active_index_version "
                + "WHERE g.course_id=? AND g.revoked_at IS NULL AND g.read_excerpt=TRUE "
                + "AND i.state IN ('KEYWORD_READY','HYBRID_READY') ORDER BY g.document_id,g.document_version",courseId)
                .stream().map(row -> new DocumentScope(row.get("document_id").toString(),
                        ((Number)row.get("document_version")).intValue(),row.get("active_index_version").toString())).toList();
    }

    public List<Map<String,Object>> documents(Integer courseId, int userId) {
        var before = scope(courseId,null,userId);
        var result = new ArrayList<Map<String,Object>>();
        for (var document : before) {
            var row = db.queryForMap("SELECT file_name FROM native_rag_document_version WHERE document_id=? AND document_version=?",
                    document.documentId(),document.documentVersion());
            Map<String,Object> view = new LinkedHashMap<>();
            view.put("id",document.documentId());
            view.put("documentId",document.documentId());
            view.put("documentVersion",document.documentVersion());
            view.put("indexVersion",document.indexVersion());
            view.put("title",row.get("file_name"));
            result.add(view);
        }
        requireScope(courseId,null,userId,before);
        return result;
    }

    public void requireScope(Integer courseId, Integer lessonId, int userId, List<DocumentScope> expected) {
        if (!Objects.equals(expected,scope(courseId,lessonId,userId)))
            throw new RagAccessException("409","课程资料或授权已改变，请开始新对话");
    }

    public RagSearchResponse search(Integer courseId, Integer lessonId, int userId, String query, List<DocumentScope> expected) {
        requireScope(courseId,lessonId,userId,expected);
        var rows = new ArrayList<Map<String,Object>>();
        for (var document : expected) {
            rows.addAll(db.queryForList("SELECT c.*,v.file_name AS title,i.embedding_fingerprint AS index_embedding_fingerprint,"
                    + "i.embedding_model AS index_embedding_model,i.dimensions AS index_dimensions,i.normalized AS index_normalized,"
                    + "i.chunk_revision AS index_chunk_revision,i.parser_revision AS index_parser_revision,i.state AS index_state,"
                    + "i.document_version AS index_document_version,i.parse_revision AS index_parse_revision "
                    + "FROM native_rag_chunk c JOIN native_rag_document_version v ON v.document_id=c.document_id "
                    + "AND v.document_version=c.document_version JOIN native_rag_index_version i ON i.id=c.index_version "
                    + "WHERE c.document_id=? AND c.document_version=? AND c.index_version=? AND c.parse_revision=i.parse_revision "
                    + "ORDER BY c.id LIMIT ?",document.documentId(),document.documentVersion(),document.indexVersion(),20001-rows.size()));
            if (rows.size()>20000) throw new RagAccessException("400","课程检索范围超过2万片段");
        }
        var ranked = retrieval.retrieve(query,rows,5);
        var hits = new ArrayList<RagSearchHit>();
        for (var match : ranked.ranked()) {
            var row = match.row();
            Number page = (Number)row.get("page_number");
            hits.add(new RagSearchHit(row.get("id").toString(),row.get("document_id").toString(),
                    row.get("kb_id").toString(),row.get("title").toString(),row.get("content").toString(),
                    page==null?null:page.intValue(),((Number)row.get("ordinal_number")).intValue(),match.score(),
                    "["+(hits.size()+1)+"]",List.of(),row.get("index_version").toString(),
                    ((Number)row.get("document_version")).intValue(),((Number)row.get("parse_revision")).intValue()));
        }
        requireScope(courseId,lessonId,userId,expected);
        return new RagSearchResponse(hits,"HYBRID".equals(ranked.actualMode())?"hybrid":"bm25",
                ranked.warning(),ranked.actualMode(),ranked.degradedReason(),List.of());
    }
}
